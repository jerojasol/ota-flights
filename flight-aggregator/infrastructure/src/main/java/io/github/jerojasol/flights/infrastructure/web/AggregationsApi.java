package io.github.jerojasol.flights.infrastructure.web;

import io.github.jerojasol.flights.infrastructure.web.dto.AggregationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI description of the admin aggregation endpoint. */
@Tag(name = "Admin", description = "Operational endpoints")
public interface AggregationsApi {

    @Operation(summary = "Aggregate a route and date now",
            description = "Takes the aggregation lock, calls the three providers synchronously, marks the route fresh "
                    + "if any provider succeeded, and returns the outcome of each.")
    @ApiResponse(responseCode = "200", description = "Aggregation finished; see the outcome per provider")
    @ApiResponse(responseCode = "400", description = "Invalid body (Problem Details)")
    @ApiResponse(responseCode = "409", description = "Another aggregation of this route is running, or the lock service is down")
    @ApiResponse(responseCode = "422", description = "Body valid but not a possible route, e.g. origin equals destination")
    AggregationResponse aggregate(AggregationRequest request);
}
