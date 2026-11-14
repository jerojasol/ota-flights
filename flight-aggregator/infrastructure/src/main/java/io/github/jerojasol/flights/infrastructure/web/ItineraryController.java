package io.github.jerojasol.flights.infrastructure.web;

import io.github.jerojasol.flights.domain.port.in.SearchItinerariesUseCase;
import io.github.jerojasol.flights.infrastructure.web.dto.ItineraryPageResponse;
import io.github.jerojasol.flights.infrastructure.web.mapper.ItineraryResponseMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryController implements ItinerariesApi {

    private final SearchItinerariesUseCase searchItineraries;
    private final SearchRequestMapper requests;

    public ItineraryController(SearchItinerariesUseCase searchItineraries, SearchRequestMapper requests) {
        this.searchItineraries = searchItineraries;
        this.requests = requests;
    }

    @Override
    @GetMapping
    public ItineraryPageResponse search(@Valid ItinerarySearchRequest request) {
        return ItineraryResponseMapper.page(searchItineraries.search(
                requests.criteria(request), requests.filter(request), requests.cursor(request), requests.limit(request)));
    }
}
