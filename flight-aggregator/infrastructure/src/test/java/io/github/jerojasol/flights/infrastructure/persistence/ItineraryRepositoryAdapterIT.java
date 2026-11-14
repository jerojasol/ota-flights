package io.github.jerojasol.flights.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.ItineraryPage;
import io.github.jerojasol.flights.domain.model.MarkupPolicy;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.PercentageMarkupPolicy;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceConfiguration.class)
@Testcontainers
// Each call commits on its own, like in production. A shared test transaction would let Hibernate serve
// cached entities after a JDBC upsert and hide what the database really holds.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ItineraryRepositoryAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final Airport MAD = new Airport("MAD");
    private static final Airport JFK = new Airport("JFK");
    private static final Airport LHR = new Airport("LHR");
    private static final LocalDate DATE = LocalDate.parse("2026-11-20");
    private static final Instant MORNING = Instant.parse("2026-11-20T07:35:00Z");
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final SearchCriteria MAD_JFK = new SearchCriteria(MAD, JFK, DATE);
    private static final MarkupPolicy FIVE_PERCENT = new PercentageMarkupPolicy(new BigDecimal("5"));
    private static final Comparator<Itinerary> PAGE_ORDER = Comparator
            .comparing((Itinerary itinerary) -> itinerary.finalPrice().amount())
            .thenComparing(itinerary -> itinerary.key().value());

    @Autowired
    private ItineraryRepositoryPort repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTable() {
        jdbc.execute("TRUNCATE itineraries");
    }

    @Test
    void should_return_inserted_itineraries_ordered_by_final_price_then_key_when_upserted() {
        Itinerary expensive = direct(Provider.ALPHA, "IB6251", MORNING, "300.00");
        Itinerary cheap = direct(Provider.BETA, "UX91", MORNING, "120.00");
        Itinerary middle = direct(Provider.GAMMA, "AA101", MORNING, "200.00");

        int written = repository.upsertAll(List.of(expensive, cheap, middle));

        assertThat(written).isEqualTo(3);
        assertThat(firstPage(ItineraryFilter.none(), 20).items()).containsExactly(cheap, middle, expensive);
    }

    @Test
    void should_update_row_when_same_key_arrives_cheaper() {
        repository.upsertAll(List.of(direct(Provider.ALPHA, "IB6251", MORNING, "300.00")));
        Itinerary cheaper = direct(Provider.GAMMA, "IB6251", MORNING, "250.00");

        int written = repository.upsertAll(List.of(cheaper));

        assertThat(written).isEqualTo(1);
        Itinerary stored = single();
        assertThat(stored.finalPrice()).isEqualTo(cheaper.finalPrice());
        assertThat(stored.basePrice()).isEqualTo(Price.of("250.00", "EUR"));
        assertThat(stored.source()).isEqualTo(Provider.GAMMA);
    }

    @Test
    void should_leave_row_untouched_when_same_key_arrives_more_expensive() {
        Itinerary original = direct(Provider.ALPHA, "IB6251", MORNING, "300.00");
        repository.upsertAll(List.of(original));

        int written = repository.upsertAll(List.of(direct(Provider.BETA, "IB6251", MORNING, "350.00")));

        assertThat(written).isZero();
        Itinerary stored = single();
        assertThat(stored.finalPrice()).isEqualTo(original.finalPrice());
        assertThat(stored.source()).isEqualTo(Provider.ALPHA);
    }

    @Test
    void should_leave_row_untouched_when_same_key_arrives_cheaper_in_another_currency() {
        Itinerary euros = direct(Provider.ALPHA, "IB6251", MORNING, "100.00");
        repository.upsertAll(List.of(euros));
        Itinerary dollars = Itinerary.from(new Offer(Provider.BETA,
                        List.of(segment("IB6251", MAD, MORNING, JFK, MORNING.plus(Duration.ofHours(8)))),
                        Price.of("50.00", "USD")),
                FIVE_PERCENT, NOW);

        int written = repository.upsertAll(List.of(dollars));

        assertThat(written).isZero();
        Itinerary stored = single();
        assertThat(stored.basePrice()).isEqualTo(Price.of("100.00", "EUR"));
        assertThat(stored.finalPrice()).isEqualTo(euros.finalPrice());
        assertThat(stored.source()).isEqualTo(Provider.ALPHA);
    }

    @Test
    void should_page_without_duplicates_or_gaps_when_prices_repeat() {
        List<Itinerary> inserted = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            // Five distinct prices, five rows each: ties must be broken by the key.
            inserted.add(direct(Provider.ALPHA, "IB" + (1000 + i), MORNING.plusSeconds(60L * i),
                    (100 + (i % 5) * 10) + ".00"));
        }
        repository.upsertAll(inserted);

        ItineraryPage first = firstPage(ItineraryFilter.none(), 10);
        ItineraryPage second = repository.findBy(MAD_JFK, ItineraryFilter.none(), first.next(), 10);
        ItineraryPage third = repository.findBy(MAD_JFK, ItineraryFilter.none(), second.next(), 10);

        assertThat(first.items()).hasSize(10);
        assertThat(second.items()).hasSize(10);
        assertThat(third.items()).hasSize(5);
        assertThat(first.next()).isPresent();
        assertThat(second.next()).isPresent();
        assertThat(third.next()).isEmpty();

        List<Itinerary> all = new ArrayList<>();
        all.addAll(first.items());
        all.addAll(second.items());
        all.addAll(third.items());
        Set<ItineraryKey> keys = new HashSet<>(all.stream().map(Itinerary::key).toList());
        assertThat(keys).hasSize(25);
        assertThat(all).containsExactlyElementsOf(inserted.stream().sorted(PAGE_ORDER).toList());
    }

    @Test
    void should_exclude_itineraries_above_max_price_when_filtering_by_price() {
        Itinerary cheap = direct(Provider.ALPHA, "IB6251", MORNING, "100.00");
        Itinerary expensive = direct(Provider.ALPHA, "IB6253", MORNING, "300.00");
        repository.upsertAll(List.of(cheap, expensive));

        ItineraryPage page = firstPage(ItineraryFilter.none().withMaxPrice(cheap.finalPrice()), 20);

        assertThat(page.items()).containsExactly(cheap);
    }

    @Test
    void should_return_no_rows_when_max_price_is_in_another_currency() {
        Itinerary cheap = direct(Provider.ALPHA, "IB6251", MORNING, "100.00");
        repository.upsertAll(List.of(cheap));

        ItineraryPage page = firstPage(ItineraryFilter.none().withMaxPrice(Price.of("1000.00", "USD")), 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.next()).isEmpty();
    }

    @Test
    void should_return_no_rows_when_cursor_price_is_in_another_currency() {
        repository.upsertAll(List.of(direct(Provider.ALPHA, "IB6251", MORNING, "100.00"),
                direct(Provider.ALPHA, "IB6253", MORNING, "200.00")));
        PageCursor usdCursor = new PageCursor(Price.of("1.00", "USD"), ItineraryKey.fromStored("0".repeat(64)));

        ItineraryPage page = repository.findBy(MAD_JFK, ItineraryFilter.none(), Optional.of(usdCursor), 20);

        assertThat(page.items()).isEmpty();
    }

    @Test
    void should_return_empty_page_when_max_price_and_cursor_currencies_differ() {
        repository.upsertAll(List.of(direct(Provider.ALPHA, "IB6251", MORNING, "100.00")));
        PageCursor eurCursor = new PageCursor(Price.of("1.00", "EUR"), ItineraryKey.fromStored("0".repeat(64)));

        ItineraryPage page = repository.findBy(MAD_JFK,
                ItineraryFilter.none().withMaxPrice(Price.of("1000.00", "USD")), Optional.of(eurCursor), 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.next()).isEmpty();
    }

    @Test
    void should_match_any_segment_carrier_when_filtering_by_carrier() {
        Itinerary ibThenAa = connecting(Provider.ALPHA, "IB3100", "AA200", "400.00");
        Itinerary uxOnly = direct(Provider.BETA, "UX91", MORNING, "150.00");
        repository.upsertAll(List.of(ibThenAa, uxOnly));

        assertThat(firstPage(ItineraryFilter.none().withCarrier(Carrier.of("AA")), 20).items())
                .containsExactly(ibThenAa);
        assertThat(firstPage(ItineraryFilter.none().withCarrier(Carrier.of("IB")), 20).items())
                .containsExactly(ibThenAa);
        assertThat(firstPage(ItineraryFilter.none().withCarrier(Carrier.of("UX")), 20).items())
                .containsExactly(uxOnly);
    }

    @Test
    void should_compose_filters_with_cursor_when_paging_a_filtered_search() {
        List<Itinerary> inserted = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inserted.add(direct(Provider.ALPHA, "IB" + (2000 + i), MORNING.plusSeconds(60L * i), (100 + i) + ".00"));
            inserted.add(direct(Provider.ALPHA, "UX" + (2000 + i), MORNING.plusSeconds(60L * i), (100 + i) + ".00"));
        }
        repository.upsertAll(inserted);
        ItineraryFilter onlyIberia = ItineraryFilter.none().withCarrier(Carrier.of("IB"));

        ItineraryPage first = firstPage(onlyIberia, 4);
        ItineraryPage second = repository.findBy(MAD_JFK, onlyIberia, first.next(), 4);

        assertThat(first.items()).hasSize(4);
        assertThat(second.items()).hasSize(2);
        assertThat(second.next()).isEmpty();
        assertThat(first.items()).extracting(it -> it.carriers().getFirst().code()).containsOnly("IB");
        assertThat(second.items()).extracting(it -> it.carriers().getFirst().code()).containsOnly("IB");
    }

    @Test
    void should_return_empty_page_when_filter_route_contradicts_criteria() {
        repository.upsertAll(List.of(direct(Provider.ALPHA, "IB6251", MORNING, "100.00")));

        assertThat(firstPage(ItineraryFilter.none().withOrigin(LHR), 20).items()).isEmpty();
    }

    @Test
    void should_preserve_key_prices_segments_and_source_when_round_tripping_three_segments() {
        Itinerary threeLegs = Itinerary.from(new Offer(Provider.BETA, List.of(
                segment("IB3100", MAD, MORNING, LHR, MORNING.plus(Duration.ofHours(2))),
                segment("BA117", LHR, MORNING.plus(Duration.ofHours(3)), new Airport("BOS"), MORNING.plus(Duration.ofHours(10))),
                segment("AA45", new Airport("BOS"), MORNING.plus(Duration.ofHours(11)), JFK, MORNING.plus(Duration.ofHours(12)))),
                Price.of("512.30", "EUR")), FIVE_PERCENT, NOW);

        repository.upsertAll(List.of(threeLegs));
        Itinerary loaded = single();

        assertThat(loaded.key()).isEqualTo(threeLegs.key());
        assertThat(loaded.key()).isEqualTo(ItineraryKey.of(loaded.segments()));
        assertThat(loaded.basePrice()).isEqualTo(threeLegs.basePrice());
        assertThat(loaded.finalPrice()).isEqualTo(threeLegs.finalPrice());
        assertThat(loaded.segments()).containsExactlyElementsOf(threeLegs.segments());
        assertThat(loaded.source()).isEqualTo(Provider.BETA);
        assertThat(loaded.ingestedAt()).isEqualTo(NOW);
    }

    private ItineraryPage firstPage(ItineraryFilter filter, int limit) {
        return repository.findBy(MAD_JFK, filter, Optional.<PageCursor>empty(), limit);
    }

    private Itinerary single() {
        List<Itinerary> items = firstPage(ItineraryFilter.none(), 20).items();
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private static Itinerary direct(Provider provider, String flight, Instant departure, String amount) {
        return Itinerary.from(new Offer(provider,
                        List.of(segment(flight, MAD, departure, JFK, departure.plus(Duration.ofHours(8)))),
                        Price.of(amount, "EUR")),
                FIVE_PERCENT, NOW);
    }

    private static Itinerary connecting(Provider provider, String first, String second, String amount) {
        return Itinerary.from(new Offer(provider, List.of(
                        segment(first, MAD, MORNING, LHR, MORNING.plus(Duration.ofHours(2))),
                        segment(second, LHR, MORNING.plus(Duration.ofHours(3)), JFK, MORNING.plus(Duration.ofHours(11)))),
                        Price.of(amount, "EUR")),
                FIVE_PERCENT, NOW);
    }

    private static Segment segment(String flight, Airport from, Instant departure, Airport to, Instant arrival) {
        return new Segment(FlightNumber.of(flight), from, departure, to, arrival);
    }
}
