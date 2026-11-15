package io.github.jerojasol.flights.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-side view of a row in {@code itineraries}. Writes go through {@link ItineraryJdbcUpserter}. Only the columns
 * needed to rebuild an Itinerary are mapped; the denormalised search columns (origin, destination, departure_date,
 * first_carrier, carriers) exist for the indexes and are written by the upserter.
 */
@Entity
@Table(name = "itineraries")
class ItineraryJpaEntity {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "itinerary_key", length = 64, nullable = false)
    private String itineraryKey;

    @Column(name = "base_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Column(name = "final_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal finalPrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "source", length = 8, nullable = false)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segments", nullable = false)
    private String segments;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected ItineraryJpaEntity() {
    }

    Long id() {
        return id;
    }

    String itineraryKey() {
        return itineraryKey;
    }

    BigDecimal basePrice() {
        return basePrice;
    }

    BigDecimal finalPrice() {
        return finalPrice;
    }

    String currency() {
        return currency;
    }

    String source() {
        return source;
    }

    String segments() {
        return segments;
    }

    Instant ingestedAt() {
        return ingestedAt;
    }
}
