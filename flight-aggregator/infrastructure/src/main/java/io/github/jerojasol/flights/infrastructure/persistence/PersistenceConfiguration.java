package io.github.jerojasol.flights.infrastructure.persistence;

import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the persistence adapter. Lives in this package (not in infrastructure.config) because the entity, the
 * Spring Data repository and the helpers are package-private; only {@link ItineraryRepositoryPort} leaves it.
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = PersistenceConfiguration.class)
public class PersistenceConfiguration {

    @Bean
    ItineraryRepositoryPort itineraryRepositoryPort(ItineraryJpaRepository reads, JdbcTemplate jdbc) {
        ItineraryPersistenceMapper mapper = new ItineraryPersistenceMapper(JsonMapper.builder().build());
        return new ItineraryRepositoryAdapter(reads, new ItineraryJdbcUpserter(jdbc), mapper);
    }
}
