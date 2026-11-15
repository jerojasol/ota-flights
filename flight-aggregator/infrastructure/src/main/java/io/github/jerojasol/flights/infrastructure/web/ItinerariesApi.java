package io.github.jerojasol.flights.infrastructure.web;

import io.github.jerojasol.flights.infrastructure.web.dto.ItineraryPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

/** OpenAPI description of the search endpoint. Kept off the controller so it depends only on ports and web types. */
@Tag(name = "Itineraries", description = "Search aggregated itineraries")
public interface ItinerariesApi {

    @Operation(summary = "Search itineraries for a route and date",
            description = "Cheapest first, keyset-paginated. Pass `nextCursor` back as `cursor` for the next page.")
    @ApiResponse(responseCode = "200", description = "A page of itineraries")
    @ApiResponse(responseCode = "400", description = "Invalid parameters or cursor (Problem Details)")
    @ApiResponse(responseCode = "422", description = "Parameters valid but not a possible search, e.g. origin equals destination")
    ItineraryPageResponse search(@ParameterObject ItinerarySearchRequest request);
}
