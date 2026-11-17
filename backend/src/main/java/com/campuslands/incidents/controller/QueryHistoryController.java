package com.campuslands.incidents.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campuslands.incidents.dto.history.QueryHistoryResponse;
import com.campuslands.incidents.dto.history.RatingRequest;
import com.campuslands.incidents.dto.history.RatingResponse;
import com.campuslands.incidents.exception.ApiError;
import com.campuslands.incidents.service.history.QueryHistoryService;
import com.campuslands.incidents.service.history.RatingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * AI history and rating (RF08). The history route sits next to member C's
 * {@code POST /api/incidents/{id}/assistance}, which creates the queries shown here.
 */
@RestController
@Tag(name = "History and rating", description = "AI queries of an incident and the rating of their answers (RF08)")
public class QueryHistoryController {

    private final QueryHistoryService historyService;
    private final RatingService ratingService;

    public QueryHistoryController(QueryHistoryService historyService, RatingService ratingService) {
        this.historyService = historyService;
        this.ratingService = ratingService;
    }

    @GetMapping("/api/incidents/{incidentId}/queries")
    @Operation(summary = "AI history of an incident, newest first (404 if the incident does not exist)",
            description = "Each query has its status (PENDING, ANSWERED, NO_RESULTS, ERROR), date, answer, "
                    + "sources with similarity and rating. Use responseId to rate an answer.")
    @ApiResponse(responseCode = "200", description = "History of the incident; empty if it has no AI queries")
    @ApiResponse(responseCode = "404", description = "The incident does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<QueryHistoryResponse> history(@PathVariable Long incidentId) {
        return historyService.findByIncident(incidentId);
    }

    @PostMapping("/api/responses/{responseId}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Rate an AI answer from 1 to 5 with an optional comment",
            description = "One rating per answer: a second one answers 409. 404 if the answer does not exist.")
    @ApiResponse(responseCode = "201", description = "Rating saved")
    @ApiResponse(responseCode = "400", description = "Score missing or outside 1-5, or comment longer than 500 characters",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "The answer does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The answer was already rated",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public RatingResponse rate(@PathVariable Long responseId, @Valid @RequestBody RatingRequest request) {
        return ratingService.rate(responseId, request);
    }
}
