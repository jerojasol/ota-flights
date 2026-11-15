package io.github.jerojasol.flights.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import io.github.jerojasol.flights.application.SearchItinerariesService;
import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.aggregation.ProviderOutcome;
import io.github.jerojasol.flights.domain.aggregation.ProviderStatus;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;
import io.github.jerojasol.flights.domain.search.Freshness;
import io.github.jerojasol.flights.domain.search.ItineraryFilter;
import io.github.jerojasol.flights.domain.search.ItineraryPage;
import io.github.jerojasol.flights.domain.search.LockToken;
import io.github.jerojasol.flights.domain.search.PageCursor;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import io.github.jerojasol.flights.domain.search.SearchResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Testcontainers
class RedisFreshnessAdapterIT {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    private static final SearchCriteria MAD_JFK =
            new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2026-11-20"));
    private static final Duration SHORT = Duration.ofSeconds(1);
    private static final Duration LONG = Duration.ofMinutes(10);
    private static final AggregationResult RESULT = new AggregationResult(3,
            Map.of(Provider.ALPHA, new ProviderOutcome(ProviderStatus.OK, 5, 2, Duration.ofMillis(412)),
                    Provider.GAMMA, new ProviderOutcome(ProviderStatus.TIMEOUT, 0, 0, Duration.ofSeconds(3))),
            Instant.parse("2026-11-14T10:00:00Z"));

    private LettuceConnectionFactory connections;
    private StringRedisTemplate template;
    private RedisFreshnessAdapter adapter;

    @BeforeEach
    void connectAndClean() {
        connections = connectTo(redis.getHost(), redis.getFirstMappedPort());
        template = new StringRedisTemplate(connections);
        adapter = new RedisFreshnessAdapter(template);
        template.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @AfterEach
    void disconnect() {
        connections.destroy();
    }

    @Test
    void should_report_fresh_after_markFresh_and_stale_after_freshness_ttl_expires() {
        assertThat(adapter.status(MAD_JFK)).isEqualTo(Freshness.MISSING);

        adapter.markFresh(MAD_JFK, RESULT, SHORT);
        assertThat(adapter.status(MAD_JFK)).isEqualTo(Freshness.FRESH);

        await().atMost(Duration.ofSeconds(5)).until(() -> adapter.status(MAD_JFK) == Freshness.STALE);
    }

    @Test
    void should_grant_lock_once_and_again_after_release() {
        Optional<LockToken> first = adapter.tryLock(MAD_JFK, LONG);
        assertThat(first).isPresent();
        assertThat(adapter.tryLock(MAD_JFK, LONG)).isEmpty();
        assertThat(adapter.status(MAD_JFK)).isEqualTo(Freshness.REFRESHING);

        adapter.release(MAD_JFK, first.get());

        assertThat(adapter.tryLock(MAD_JFK, LONG)).isPresent();
    }

    @Test
    void should_free_lock_when_lock_ttl_expires() {
        assertThat(adapter.tryLock(MAD_JFK, SHORT)).isPresent();

        await().atMost(Duration.ofSeconds(5)).until(() -> adapter.status(MAD_JFK) != Freshness.REFRESHING);
        assertThat(adapter.tryLock(MAD_JFK, LONG)).isPresent();
    }

    @Test
    void should_not_delete_a_lock_re_acquired_by_another_caller_when_expired_owner_releases() {
        LockToken expiredOwner = adapter.tryLock(MAD_JFK, SHORT).orElseThrow();
        await().atMost(Duration.ofSeconds(5)).until(() -> adapter.status(MAD_JFK) != Freshness.REFRESHING);
        LockToken currentOwner = adapter.tryLock(MAD_JFK, LONG).orElseThrow();

        adapter.release(MAD_JFK, expiredOwner);

        assertThat(adapter.status(MAD_JFK)).as("the current owner's lock must survive").isEqualTo(Freshness.REFRESHING);
        assertThat(adapter.tryLock(MAD_JFK, LONG)).as("a third caller must still be refused").isEmpty();
        adapter.release(MAD_JFK, currentOwner);
        assertThat(adapter.tryLock(MAD_JFK, LONG)).isPresent();
    }

    @Test
    void should_return_last_result_with_outcomes_even_after_freshness_ttl_expires() {
        adapter.markFresh(MAD_JFK, RESULT, SHORT);

        await().atMost(Duration.ofSeconds(5)).until(() -> adapter.status(MAD_JFK) == Freshness.STALE);

        assertThat(adapter.lastResult(MAD_JFK)).contains(RESULT);
    }

    @Test
    void should_fail_closed_without_exception_when_redis_is_unreachable() {
        LettuceConnectionFactory down = connectTo("localhost", 1);
        RedisFreshnessAdapter unavailable = new RedisFreshnessAdapter(new StringRedisTemplate(down));
        try {
            assertThat(unavailable.status(MAD_JFK)).isEqualTo(Freshness.STALE);
            assertThat(unavailable.tryLock(MAD_JFK, LONG)).isEmpty();
            unavailable.markFresh(MAD_JFK, RESULT, LONG);
            unavailable.release(MAD_JFK, new LockToken("any"));
            assertThat(unavailable.lastResult(MAD_JFK)).isEmpty();
        } finally {
            down.destroy();
        }
    }

    @Test
    void should_never_call_aggregator_and_report_stale_when_concurrent_searches_run_with_redis_down()
            throws InterruptedException {
        LettuceConnectionFactory down = connectTo("localhost", 1);
        RedisFreshnessAdapter unavailable = new RedisFreshnessAdapter(new StringRedisTemplate(down));
        AtomicInteger aggregations = new AtomicInteger();
        SearchItinerariesService service = new SearchItinerariesService(new EmptyRepository(), unavailable,
                criteria -> {
                    aggregations.incrementAndGet();
                    return RESULT;
                },
                Runnable::run, LONG, SHORT);
        ExecutorService searches = Executors.newFixedThreadPool(16);
        try {
            List<Future<SearchResult>> results = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                results.add(searches.submit(() ->
                        service.search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20)));
            }
            for (Future<SearchResult> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).freshness()).isEqualTo(Freshness.STALE);
            }
            assertThat(aggregations).hasValue(0);
        } catch (ExecutionException | TimeoutException failed) {
            throw new AssertionError(failed);
        } finally {
            searches.shutdownNow();
            down.destroy();
        }
    }

    @Test
    void should_discard_corrupt_seen_value_and_report_missing_when_it_cannot_be_decoded() {
        template.opsForValue().set("seen:MAD:JFK:2026-11-20", "{not json", Duration.ofDays(7));

        assertThat(adapter.status(MAD_JFK)).isEqualTo(Freshness.STALE);
        assertThat(adapter.lastResult(MAD_JFK)).isEmpty();
        assertThat(adapter.status(MAD_JFK)).isEqualTo(Freshness.MISSING);
    }

    @Test
    void should_keep_valid_result_written_after_corrupt_value_was_read() {
        template.opsForValue().set("seen:MAD:JFK:2026-11-20", "{not json", Duration.ofDays(7));
        RedisFreshnessAdapter raced = new RedisFreshnessAdapter(new StringRedisTemplate(connections) {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                adapter.markFresh(MAD_JFK, RESULT, LONG);
                return super.execute(script, keys, args);
            }
        });

        assertThat(raced.lastResult(MAD_JFK)).isEmpty();
        assertThat(adapter.lastResult(MAD_JFK)).contains(RESULT);
    }

    @Test
    void should_discard_seen_value_naming_a_removed_provider_and_report_missing() {
        template.opsForValue().set("seen:MAD:JFK:2026-11-20",
                "{\"persisted\":1,\"aggregatedAt\":\"2026-11-14T10:00:00Z\","
                        + "\"outcomes\":{\"ZETA\":{\"status\":\"OK\",\"offersReceived\":1,"
                        + "\"offersRejected\":0,\"elapsedNanos\":1}}}",
                Duration.ofDays(7));

        assertThat(adapter.lastResult(MAD_JFK)).isEmpty();
        assertThat(adapter.status(MAD_JFK)).isEqualTo(Freshness.MISSING);
    }

    @Test
    void should_keep_seen_outliving_fresh_by_twice_the_freshness_ttl_when_that_is_longer_than_seven_days() {
        adapter.markFresh(MAD_JFK, RESULT, Duration.ofDays(4));

        long seenSeconds = template.getExpire("seen:MAD:JFK:2026-11-20", TimeUnit.SECONDS);
        assertThat(seenSeconds).isGreaterThan(Duration.ofDays(7).toSeconds());
        assertThat(seenSeconds).isLessThanOrEqualTo(Duration.ofDays(8).toSeconds());
    }

    @Test
    void should_keep_seen_for_seven_days_when_freshness_ttl_is_short() {
        adapter.markFresh(MAD_JFK, RESULT, LONG);

        long seenSeconds = template.getExpire("seen:MAD:JFK:2026-11-20", TimeUnit.SECONDS);
        assertThat(seenSeconds).isBetween(Duration.ofDays(7).toSeconds() - 60, Duration.ofDays(7).toSeconds());
    }

    /** A repository with nothing in it: a cold route. */
    private static final class EmptyRepository implements ItineraryRepositoryPort {

        @Override
        public int upsertAll(Collection<Itinerary> itineraries) {
            return 0;
        }

        @Override
        public ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor,
                                    int limit) {
            return ItineraryPage.empty();
        }
    }

    private static LettuceConnectionFactory connectTo(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        return factory;
    }
}
