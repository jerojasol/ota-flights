package io.github.jerojasol.mockproviders.generator;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import io.github.jerojasol.mockproviders.config.MockProperties;
import org.springframework.stereotype.Component;

/**
 * Deterministic inventory: the whole base inventory for {@code (origin, destination, date)} is generated
 * from {@code Random(Objects.hash(seed, origin, destination, date))} independently of the requested
 * provider, then filtered. That is what makes overlap computable across providers.
 */
@Component
public class InventoryGenerator {

    static final List<String> HUBS = List.of("LHR", "CDG", "AMS", "FRA", "MAD", "JFK");
    static final List<String> CARRIERS = List.of("IB", "BA", "AF", "KL", "LH", "UX", "DL", "AA");
    static final String CURRENCY = "EUR";

    private static final int SLOT_MINUTES = 5;
    private static final int MIN_SEGMENT_MINUTES = 60;
    private static final int MAX_SEGMENT_MINUTES = 9 * 60;
    private static final int MIN_CONNECTION_MINUTES = 50;
    private static final int MAX_CONNECTION_MINUTES = 5 * 60;
    private static final int MAX_PRICE_SPREAD_BASIS_POINTS = 500;

    private final MockProperties properties;

    public InventoryGenerator(MockProperties properties) {
        this.properties = properties;
    }

    /** Itineraries offered by {@code provider}, each carrying that provider's price. */
    public List<GeneratedItinerary> generate(String origin, String destination, LocalDate date, ProviderId provider) {
        Objects.requireNonNull(provider, "provider");
        return build(origin, destination, date).stream()
                .filter(e -> e.prices().containsKey(provider))
                .map(e -> e.toItinerary(e.prices().get(provider)))
                .toList();
    }

    /** The provider-independent inventory, each itinerary carrying its base price. */
    public List<GeneratedItinerary> baseInventory(String origin, String destination, LocalDate date) {
        return build(origin, destination, date).stream()
                .map(e -> e.toItinerary(e.basePriceCents()))
                .toList();
    }

    private List<Entry> build(String origin, String destination, LocalDate date) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(date, "date");
        Random rnd = new Random(Objects.hash(properties.seed(), origin, destination, date));
        int size = properties.inventorySize();
        List<String> hubs = HUBS.stream().filter(h -> !h.equals(origin) && !h.equals(destination)).toList();
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();

        // 1. base itineraries
        List<List<GeneratedSegment>> segmentsByIndex = new ArrayList<>(size);
        long[] basePrices = new long[size];
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < size; i++) {
            List<GeneratedSegment> segments;
            do {
                segments = randomSegments(rnd, origin, destination, dayStart, hubs, connectionsFor(rnd));
            } while (!seenIds.add(GeneratedItinerary.canonicalIdOf(segments)));
            segmentsByIndex.add(segments);
            basePrices[i] = basePrice(rnd, segments);
        }

        // 2. overlap and per-provider prices
        boolean[] overlapping = pick(rnd, size, properties.overlapRatio());
        List<Map<ProviderId, Long>> prices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Set<ProviderId> offeredBy = overlapping[i] ? randomSubset(rnd, 2 + rnd.nextInt(2)) : randomSubset(rnd, 1);
            prices.add(providerPrices(rnd, basePrices[i], offeredBy));
        }

        // 3. invalid itineraries, kinds assigned round-robin so every kind shows up
        boolean[] invalid = pick(rnd, size, properties.invalidRatio());
        InvalidKind[] kinds = InvalidKind.values();
        InvalidKind[] invalidByIndex = new InvalidKind[size];
        int assigned = 0;
        for (int i = 0; i < size; i++) {
            if (!invalid[i]) {
                continue;
            }
            InvalidKind kind = kinds[assigned++ % kinds.length];
            List<GeneratedSegment> segments = segmentsByIndex.get(i);
            if (kind.needsConnection() && segments.size() < 2) {
                segments = randomSegments(rnd, origin, destination, dayStart, hubs, 1);
            }
            segmentsByIndex.set(i, mutate(rnd, kind, segments, hubs));
            invalidByIndex[i] = kind;
        }

        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(segmentsByIndex.get(i), basePrices[i], prices.get(i), invalidByIndex[i]));
        }
        return entries;
    }

    private static int connectionsFor(Random rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 60) {
            return 0;
        }
        return roll < 90 ? 1 : 2;
    }

    private static List<GeneratedSegment> randomSegments(Random rnd, String origin, String destination,
                                                         Instant dayStart, List<String> hubs, int connections) {
        List<String> shuffledHubs = new ArrayList<>(hubs);
        Collections.shuffle(shuffledHubs, rnd);
        List<String> airports = new ArrayList<>();
        airports.add(origin);
        airports.addAll(shuffledHubs.subList(0, connections));
        airports.add(destination);

        List<GeneratedSegment> segments = new ArrayList<>(connections + 1);
        Instant departure = dayStart.plus(Duration.ofMinutes((long) rnd.nextInt(24 * 60 / SLOT_MINUTES) * SLOT_MINUTES));
        for (int s = 0; s <= connections; s++) {
            Instant arrival = departure.plus(minutes(rnd, MIN_SEGMENT_MINUTES, MAX_SEGMENT_MINUTES));
            segments.add(new GeneratedSegment(
                    CARRIERS.get(rnd.nextInt(CARRIERS.size())),
                    1 + rnd.nextInt(9999),
                    airports.get(s),
                    airports.get(s + 1),
                    departure,
                    arrival));
            departure = arrival.plus(minutes(rnd, MIN_CONNECTION_MINUTES, MAX_CONNECTION_MINUTES));
        }
        return segments;
    }

    /** Random duration in {@code [min, max]} minutes at 5-minute granularity. */
    private static Duration minutes(Random rnd, int min, int max) {
        return Duration.ofMinutes(min + (long) rnd.nextInt((max - min) / SLOT_MINUTES + 1) * SLOT_MINUTES);
    }

    private static long basePrice(Random rnd, List<GeneratedSegment> segments) {
        long flownMinutes = segments.stream()
                .mapToLong(s -> Duration.between(s.departureUtc(), s.arrivalUtc()).toMinutes())
                .sum();
        long centsPerHour = 4_000 + rnd.nextInt(5_001);
        return 3_000 + flownMinutes * centsPerHour / 60;
    }

    private static boolean[] pick(Random rnd, int size, double ratio) {
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, rnd);
        boolean[] picked = new boolean[size];
        int count = (int) Math.round(size * ratio);
        for (int i = 0; i < count; i++) {
            picked[indices.get(i)] = true;
        }
        return picked;
    }

    private static Set<ProviderId> randomSubset(Random rnd, int count) {
        List<ProviderId> all = new ArrayList<>(List.of(ProviderId.values()));
        Collections.shuffle(all, rnd);
        return EnumSet.copyOf(all.subList(0, count));
    }

    /** Base price × (1 ± up to 5%), rounded to cents, distinct per provider. */
    private static Map<ProviderId, Long> providerPrices(Random rnd, long basePrice, Set<ProviderId> offeredBy) {
        Map<ProviderId, Long> prices = new EnumMap<>(ProviderId.class);
        Set<Long> used = new HashSet<>();
        for (ProviderId provider : offeredBy) {
            long price;
            do {
                int basisPoints = rnd.nextInt(2 * MAX_PRICE_SPREAD_BASIS_POINTS + 1) - MAX_PRICE_SPREAD_BASIS_POINTS;
                price = Math.round(basePrice * (1 + basisPoints / 10_000.0));
            } while (!used.add(price));
            prices.put(provider, price);
        }
        return prices;
    }

    private static List<GeneratedSegment> mutate(Random rnd, InvalidKind kind, List<GeneratedSegment> original,
                                                 List<String> hubs) {
        List<GeneratedSegment> segments = new ArrayList<>(original);
        int leg = rnd.nextInt(segments.size());
        int connection = 1 + rnd.nextInt(Math.max(1, segments.size() - 1));
        switch (kind) {
            case DEAD_LEG_SAME_AIRPORT -> {
                GeneratedSegment s = segments.get(leg);
                segments.set(leg, new GeneratedSegment(s.carrier(), s.flightNumber(), s.from(), s.from(),
                        s.departureUtc(), s.arrivalUtc()));
            }
            case DEAD_LEG_NON_POSITIVE_DURATION -> {
                GeneratedSegment s = segments.get(leg);
                Instant arrival = s.departureUtc().minus(Duration.ofMinutes((long) rnd.nextInt(13) * SLOT_MINUTES));
                segments.set(leg, new GeneratedSegment(s.carrier(), s.flightNumber(), s.from(), s.to(),
                        s.departureUtc(), arrival));
            }
            case CONNECTION_AIRPORT_MISMATCH -> {
                GeneratedSegment previous = segments.get(connection - 1);
                GeneratedSegment s = segments.get(connection);
                List<String> candidates = hubs.stream()
                        .filter(h -> !h.equals(previous.to()) && !h.equals(s.to()))
                        .toList();
                String wrongFrom = candidates.get(rnd.nextInt(candidates.size()));
                segments.set(connection, new GeneratedSegment(s.carrier(), s.flightNumber(), wrongFrom, s.to(),
                        s.departureUtc(), s.arrivalUtc()));
            }
            case CONNECTION_OVERLAP -> shiftFrom(segments, connection,
                    segments.get(connection - 1).arrivalUtc().minus(minutes(rnd, 5, 60)));
            case CONNECTION_TOO_SHORT -> shiftFrom(segments, connection,
                    segments.get(connection - 1).arrivalUtc().plus(minutes(rnd, 5, 15)));
        }
        return segments;
    }

    /** Moves segment {@code index} to depart at {@code newDeparture}, shifting every later segment equally. */
    private static void shiftFrom(List<GeneratedSegment> segments, int index, Instant newDeparture) {
        Duration delta = Duration.between(segments.get(index).departureUtc(), newDeparture);
        for (int i = index; i < segments.size(); i++) {
            GeneratedSegment s = segments.get(i);
            segments.set(i, new GeneratedSegment(s.carrier(), s.flightNumber(), s.from(), s.to(),
                    s.departureUtc().plus(delta), s.arrivalUtc().plus(delta)));
        }
    }

    private record Entry(List<GeneratedSegment> segments, long basePriceCents, Map<ProviderId, Long> prices,
                         InvalidKind invalid) {

        GeneratedItinerary toItinerary(long priceCents) {
            return new GeneratedItinerary(GeneratedItinerary.canonicalIdOf(segments), segments, priceCents,
                    CURRENCY, prices.keySet(), invalid);
        }
    }
}
