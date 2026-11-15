package io.github.jerojasol.flights.domain.port.out;

import io.github.jerojasol.flights.domain.aggregation.ProviderResponse;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

public interface FlightProviderPort {

    Provider provider();

    /** @throws io.github.jerojasol.flights.domain.exception.ProviderUnavailableException when the provider cannot deliver */
    ProviderResponse fetch(SearchCriteria criteria);
}
