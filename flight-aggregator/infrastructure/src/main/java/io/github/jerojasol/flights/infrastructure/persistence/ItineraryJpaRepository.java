package io.github.jerojasol.flights.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Keyset pages ordered by (final_price, itinerary_key), served by {@code ix_itineraries_search}. Callers ask for
 * {@code limit + 1} rows: the extra row only signals that a next page exists, so no COUNT is needed.
 * <p>
 * The first page and later pages are separate statements on purpose. A cursor written as
 * {@code (:cursorPrice IS NULL OR (final_price, itinerary_key) > (...))} cannot become an index condition under a
 * generic plan, so Postgres would read every row before the cursor and the cost of a page would grow with its depth.
 * The optional filters (max price, carrier, currency) keep the {@code IS NULL OR} form: they only narrow rows the
 * index range already selected. The casts give Postgres a type for a null parameter, and the route parameters are
 * cast to {@code char(3)} because the driver sends strings as varchar: comparing a char column with a varchar casts
 * the column to text, and the index can no longer be used.
 */
interface ItineraryJpaRepository extends JpaRepository<ItineraryJpaEntity, Long> {

    String ROUTE_AND_FILTERS = """
            SELECT * FROM itineraries
            WHERE origin = CAST(:origin AS char(3)) AND destination = CAST(:destination AS char(3))
              AND departure_date = :date
              AND (CAST(:maxPrice AS numeric) IS NULL OR final_price <= CAST(:maxPrice AS numeric))
              AND (CAST(:carrier AS char(2)) IS NULL OR carriers @> ARRAY[CAST(:carrier AS char(2))])
              AND (CAST(:currency AS char(3)) IS NULL OR currency = CAST(:currency AS char(3)))
            """;

    String ORDER_AND_LIMIT = """
            ORDER BY final_price, itinerary_key
            LIMIT :limitPlusOne
            """;

    String FIRST_PAGE = ROUTE_AND_FILTERS + ORDER_AND_LIMIT;

    String NEXT_PAGE = ROUTE_AND_FILTERS + """
              AND (final_price, itinerary_key) > (:cursorPrice, CAST(:cursorKey AS char(64)))
            """ + ORDER_AND_LIMIT;

    @Query(nativeQuery = true, value = FIRST_PAGE)
    List<ItineraryJpaEntity> findFirstPage(@Param("origin") String origin,
                                           @Param("destination") String destination,
                                           @Param("date") LocalDate date,
                                           @Param("maxPrice") BigDecimal maxPrice,
                                           @Param("carrier") String carrier,
                                           @Param("currency") String currency,
                                           @Param("limitPlusOne") int limitPlusOne);

    @Query(nativeQuery = true, value = NEXT_PAGE)
    List<ItineraryJpaEntity> findNextPage(@Param("origin") String origin,
                                          @Param("destination") String destination,
                                          @Param("date") LocalDate date,
                                          @Param("maxPrice") BigDecimal maxPrice,
                                          @Param("carrier") String carrier,
                                          @Param("currency") String currency,
                                          @Param("cursorPrice") BigDecimal cursorPrice,
                                          @Param("cursorKey") String cursorKey,
                                          @Param("limitPlusOne") int limitPlusOne);
}
