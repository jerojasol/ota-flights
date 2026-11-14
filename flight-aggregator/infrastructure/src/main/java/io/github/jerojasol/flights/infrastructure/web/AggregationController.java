package io.github.jerojasol.flights.infrastructure.web;

import io.github.jerojasol.flights.domain.port.in.RefreshItinerariesUseCase;
import io.github.jerojasol.flights.infrastructure.web.dto.AggregationResponse;
import io.github.jerojasol.flights.infrastructure.web.mapper.ItineraryResponseMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synchronous on purpose: 200 with the outcome of each provider is simpler to demo than 202 plus polling. 409 when the
 * aggregation lock cannot be taken (another aggregation is running, or Redis is down).
 */
@RestController
@RequestMapping("/api/admin/aggregations")
public class AggregationController implements AggregationsApi {

    private final RefreshItinerariesUseCase refreshItineraries;
    private final SearchRequestMapper requests;

    public AggregationController(RefreshItinerariesUseCase refreshItineraries, SearchRequestMapper requests) {
        this.refreshItineraries = refreshItineraries;
        this.requests = requests;
    }

    @Override
    @PostMapping
    public AggregationResponse aggregate(@Valid @RequestBody AggregationRequest request) {
        return refreshItineraries.refresh(requests.criteria(request))
                .map(ItineraryResponseMapper::aggregation)
                .orElseThrow(AggregationInProgressException::new);
    }
}
