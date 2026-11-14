package io.github.jerojasol.flights.infrastructure.cache;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.DomainException;
import io.github.jerojasol.flights.domain.model.Freshness;
import io.github.jerojasol.flights.domain.model.LockToken;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis implementation of {@link FreshnessPort}. One aggregation per route and date uses three keys:
 * <pre>
 * fresh:{ORIGIN}:{DEST}:{yyyy-MM-dd}  marker "1", TTL = freshness TTL. Present → FRESH.
 * lock:{ORIGIN}:{DEST}:{yyyy-MM-dd}   owner token, SET NX, TTL = lock TTL. Present → REFRESHING.
 * seen:{ORIGIN}:{DEST}:{yyyy-MM-dd}   JSON of the last AggregationResult, TTL = 7 days. Present, but no fresh → STALE.
 * </pre>
 * The result JSON lives in {@code seen}, not in {@code fresh}: a STALE response still reports the outcomes of the last
 * aggregation (SearchItinerariesService reads them through lastResult after the freshness TTL has expired).
 *
 * <p>The lock is released by compare-and-delete in a Lua script, so an owner whose lock expired cannot remove the lock
 * of the caller that re-acquired it.
 *
 * <p><b>Degraded mode: fail closed.</b> If Redis is unreachable, nothing is aggregated and nothing is refreshed:
 * status is STALE (the search serves what Postgres holds and schedules no refresh), tryLock is empty (no caller can
 * become the holder, so no provider is called), markFresh and release do nothing, and lastResult is empty. A route
 * that was never aggregated before returns an empty page while Redis is down. A refresh of a stale route is possible
 * again only when Redis is back. Each degraded call is logged with outcome=DEGRADED.
 *
 * <p>A stored {@code seen} value that cannot be decoded (corrupt, or naming a removed enum value) is discarded and the
 * route reads as MISSING, so one bad value cannot make searches for that route fail.
 */
public final class RedisFreshnessAdapter implements FreshnessPort {

    private static final Logger LOG = LoggerFactory.getLogger(RedisFreshnessAdapter.class);
    private static final String FRESH_MARKER = "1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final DefaultRedisScript<Long> DISCARD_IF_UNCHANGED = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisFreshnessAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Outlives the freshness TTL: a STALE response still reports the outcomes of the last aggregation. */
    private static final Duration SEEN_MIN_TTL = Duration.ofDays(7);

    @Override
    public Freshness status(SearchCriteria criteria) {
        try {
            if (exists(FreshnessKeys.fresh(criteria))) {
                return Freshness.FRESH;
            }
            if (exists(FreshnessKeys.lock(criteria))) {
                return Freshness.REFRESHING;
            }
            return exists(FreshnessKeys.seen(criteria)) ? Freshness.STALE : Freshness.MISSING;
        } catch (DataAccessException unreachable) {
            degraded("status", unreachable);
            return Freshness.STALE;
        }
    }

    @Override
    public Optional<LockToken> tryLock(SearchCriteria criteria, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(FreshnessKeys.lock(criteria), token, ttl);
            return Boolean.TRUE.equals(acquired) ? Optional.of(new LockToken(token)) : Optional.empty();
        } catch (DataAccessException unreachable) {
            degraded("tryLock", unreachable);
            return Optional.empty();
        }
    }

    @Override
    public void release(SearchCriteria criteria, LockToken token) {
        try {
            redis.execute(RELEASE, List.of(FreshnessKeys.lock(criteria)), token.value());
        } catch (DataAccessException unreachable) {
            degraded("release", unreachable);
        }
    }

    @Override
    public void markFresh(SearchCriteria criteria, AggregationResult result, Duration ttl) {
        try {
            // seen first: if the second write fails, the route is STALE and the next search refreshes it.
            redis.opsForValue().set(FreshnessKeys.seen(criteria), toJson(result), seenTtl(ttl));
            redis.opsForValue().set(FreshnessKeys.fresh(criteria), FRESH_MARKER, ttl);
        } catch (DataAccessException unreachable) {
            degraded("markFresh", unreachable);
        }
    }

    @Override
    public Optional<AggregationResult> lastResult(SearchCriteria criteria) {
        String key = FreshnessKeys.seen(criteria);
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (DataAccessException unreachable) {
            degraded("lastResult", unreachable);
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromJson(json));
        } catch (JacksonException | IllegalArgumentException | DomainException corrupt) {
            LOG.warn("cache=freshness operation=lastResult outcome=CORRUPT action=discarded reason=\"{}\"",
                    corrupt.getMessage());
            discard(key, json);
            return Optional.empty();
        }
    }

    /** The seen key must outlive the fresh key, so a FRESH or STALE route always has its last outcomes. */
    private static Duration seenTtl(Duration freshnessTtl) {
        Duration twice = freshnessTtl.multipliedBy(2);
        return twice.compareTo(SEEN_MIN_TTL) > 0 ? twice : SEEN_MIN_TTL;
    }

    /** Deletes the key only while it still holds the corrupt value; a valid value written meanwhile survives. */
    private void discard(String key, String corruptJson) {
        try {
            redis.execute(DISCARD_IF_UNCHANGED, List.of(key), corruptJson);
        } catch (DataAccessException unreachable) {
            degraded("discard", unreachable);
        }
    }

    private boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    private static void degraded(String operation, DataAccessException cause) {
        LOG.warn("cache=freshness operation={} outcome=DEGRADED reason=\"{}\"", operation, cause.getMessage());
    }

    private static String toJson(AggregationResult result) {
        Map<String, OutcomeJson> byName = new LinkedHashMap<>();
        result.outcomes().forEach((provider, outcome) -> byName.put(provider.name(), new OutcomeJson(
                outcome.status().name(), outcome.offersReceived(), outcome.offersRejected(),
                outcome.elapsed().toNanos())));
        return JSON.writeValueAsString(new AggregationJson(result.persisted(), result.aggregatedAt().toString(), byName));
    }

    private static AggregationResult fromJson(String json) {
        AggregationJson record = JSON.readValue(json, AggregationJson.class);
        Map<Provider, ProviderOutcome> outcomes = new EnumMap<>(Provider.class);
        record.outcomes().forEach((name, outcome) -> outcomes.put(Provider.valueOf(name), new ProviderOutcome(
                ProviderStatus.valueOf(outcome.status()), outcome.offersReceived(), outcome.offersRejected(),
                Duration.ofNanos(outcome.elapsedNanos()))));
        return new AggregationResult(record.persisted(), outcomes, Instant.parse(record.aggregatedAt()));
    }

    /** The stored shape of one aggregation. Owned by this adapter; the domain never sees these types. */
    private record AggregationJson(int persisted, String aggregatedAt, Map<String, OutcomeJson> outcomes) {
    }

    private record OutcomeJson(String status, int offersReceived, int offersRejected, long elapsedNanos) {
    }
}
