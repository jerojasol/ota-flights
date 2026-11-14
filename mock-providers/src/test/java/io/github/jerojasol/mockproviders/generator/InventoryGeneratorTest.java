package io.github.jerojasol.mockproviders.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.jerojasol.mockproviders.config.MockProperties;
import org.junit.jupiter.api.Test;

class InventoryGeneratorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static final int SIZE = 2_000;
    private static final double OVERLAP = 0.30;
    private static final double INVALID = 0.05;

    private final InventoryGenerator generator = new InventoryGenerator(properties(SIZE));

    private static MockProperties properties(int size) {
        return new MockProperties(42, size, OVERLAP, INVALID,
                new MockProperties.Chaos(false, Duration.ofMillis(100), Duration.ofMillis(5000), 0.0));
    }

    @Test
    void should_return_identical_lists_when_inputs_are_the_same() {
        InventoryGenerator other = new InventoryGenerator(properties(SIZE));
        for (ProviderId provider : ProviderId.values()) {
            assertThat(other.generate("MAD", "JFK", DATE, provider))
                    .isEqualTo(generator.generate("MAD", "JFK", DATE, provider));
        }
    }

    @Test
    void should_return_different_lists_when_date_differs() {
        assertThat(generator.generate("MAD", "JFK", DATE.plusDays(1), ProviderId.ALPHA))
                .isNotEqualTo(generator.generate("MAD", "JFK", DATE, ProviderId.ALPHA));
    }

    @Test
    void should_offer_overlap_ratio_of_itineraries_in_several_providers_when_generating() {
        long shared = base().stream().filter(i -> i.offeredBy().size() >= 2).count();

        assertThat((double) shared / SIZE).isBetween(OVERLAP - 0.05, OVERLAP + 0.05);
    }

    @Test
    void should_share_segments_and_differ_in_price_when_itinerary_overlaps() {
        Map<String, Map<ProviderId, GeneratedItinerary>> byId = new HashMap<>();
        for (ProviderId provider : ProviderId.values()) {
            generator.generate("MAD", "JFK", DATE, provider)
                    .forEach(i -> byId.computeIfAbsent(i.canonicalId(), k -> new HashMap<>()).put(provider, i));
        }
        List<Map<ProviderId, GeneratedItinerary>> overlapping =
                byId.values().stream().filter(m -> m.size() >= 2).toList();

        assertThat(overlapping).isNotEmpty();
        for (Map<ProviderId, GeneratedItinerary> offers : overlapping) {
            List<GeneratedItinerary> list = List.copyOf(offers.values());
            assertThat(list).extracting(GeneratedItinerary::segments).containsOnly(list.getFirst().segments());
            assertThat(list).extracting(GeneratedItinerary::priceCents).doesNotHaveDuplicates();
            assertThat(list).extracting(GeneratedItinerary::offeredBy).containsOnly(offers.keySet());
        }
    }

    @Test
    void should_mark_invalid_ratio_of_itineraries_with_every_kind_when_generating() {
        List<GeneratedItinerary> base = base();
        long invalid = base.stream().filter(i -> i.invalid() != null).count();

        assertThat((double) invalid / SIZE).isBetween(INVALID - 0.05, INVALID + 0.05);
        assertThat(base.stream().map(GeneratedItinerary::invalid).filter(k -> k != null).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(InvalidKind.values());
    }

    @Test
    void should_violate_its_rule_when_itinerary_is_invalid() {
        for (GeneratedItinerary itinerary : base()) {
            if (itinerary.invalid() != null) {
                assertThat(violations(itinerary.segments())).as(itinerary.canonicalId()).contains(itinerary.invalid());
            }
        }
    }

    @Test
    void should_satisfy_all_rules_when_itinerary_is_valid() {
        for (GeneratedItinerary itinerary : base()) {
            if (itinerary.invalid() == null) {
                assertThat(violations(itinerary.segments())).as(itinerary.canonicalId()).isEmpty();
                assertThat(itinerary.segments().getFirst().from()).isEqualTo("MAD");
                assertThat(itinerary.segments().getLast().to()).isEqualTo("JFK");
            }
        }
    }

    @Test
    void should_equal_base_inventory_when_providers_are_united_by_canonical_id() {
        Set<String> union = Stream.of(ProviderId.values())
                .flatMap(p -> generator.generate("MAD", "JFK", DATE, p).stream())
                .map(GeneratedItinerary::canonicalId)
                .collect(Collectors.toSet());

        assertThat(union).isEqualTo(base().stream().map(GeneratedItinerary::canonicalId).collect(Collectors.toSet()));
    }

    @Test
    void should_include_only_requested_provider_when_generating_for_it() {
        for (ProviderId provider : ProviderId.values()) {
            assertThat(generator.generate("MAD", "JFK", DATE, provider))
                    .allSatisfy(i -> assertThat(i.offeredBy()).contains(provider));
        }
    }

    @Test
    void should_build_canonical_id_from_ordered_segments_when_generating() {
        GeneratedItinerary first = base().getFirst();

        assertThat(first.canonicalId()).isEqualTo(GeneratedItinerary.canonicalIdOf(first.segments()));
        assertThat(first.canonicalId()).matches("([A-Z]{2}[1-9]\\d{0,3}@\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z>[A-Z]{3}-[A-Z]{3}\\|?)+");
    }

    private List<GeneratedItinerary> base() {
        return generator.baseInventory("MAD", "JFK", DATE);
    }

    private static Set<InvalidKind> violations(List<GeneratedSegment> segments) {
        Set<InvalidKind> found = EnumSet.noneOf(InvalidKind.class);
        for (int i = 0; i < segments.size(); i++) {
            GeneratedSegment s = segments.get(i);
            if (s.from().equals(s.to())) {
                found.add(InvalidKind.DEAD_LEG_SAME_AIRPORT);
            }
            if (!s.arrivalUtc().isAfter(s.departureUtc())) {
                found.add(InvalidKind.DEAD_LEG_NON_POSITIVE_DURATION);
            }
            if (i > 0) {
                GeneratedSegment previous = segments.get(i - 1);
                if (!previous.to().equals(s.from())) {
                    found.add(InvalidKind.CONNECTION_AIRPORT_MISMATCH);
                }
                Duration connection = Duration.between(previous.arrivalUtc(), s.departureUtc());
                if (connection.isNegative()) {
                    found.add(InvalidKind.CONNECTION_OVERLAP);
                } else if (connection.compareTo(Duration.ofMinutes(50)) < 0) {
                    found.add(InvalidKind.CONNECTION_TOO_SHORT);
                }
            }
        }
        return found;
    }
}
