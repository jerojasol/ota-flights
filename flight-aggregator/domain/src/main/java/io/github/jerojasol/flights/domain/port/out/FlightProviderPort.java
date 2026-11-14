package io.github.jerojasol.flights.domain.port.out;

import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.SearchCriteria;

import java.util.List;

public interface FlightProviderPort {

    Provider provider();

    /** @throws io.github.jerojasol.flights.domain.model.ProviderUnavailableException when the provider cannot deliver */
    List<Offer> fetch(SearchCriteria criteria);
}
