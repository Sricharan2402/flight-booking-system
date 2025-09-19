package com.flightbooking.controller

import com.flightbooking.controller.mapper.toApiResponse
import com.flightbooking.domain.search.SearchRequest
import com.flightbooking.generated.search.api.JourneysApi
import com.flightbooking.generated.search.model.SearchResponse
import com.flightbooking.services.search.SearchService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class SearchController(
    private val searchService: SearchService
) : JourneysApi {

    private val logger = LoggerFactory.getLogger(SearchController::class.java)

    override fun searchJourneys(
        sourceAirport: String,
        destinationAirport: String,
        departureDate: LocalDate,
        passengers: Int,
        sortBy: String?,
        limit: Int?
    ): ResponseEntity<SearchResponse> {
        logger.info("Searching journeys from $sourceAirport to $destinationAirport on $departureDate for $passengers passengers")

        val searchRequest = SearchRequest(
            sourceAirport = sourceAirport,
            destinationAirport = destinationAirport,
            departureDate = departureDate,
            passengers = passengers,
            sortBy = sortBy,
            limit = limit
        )

        val searchResponse = searchService.searchJourneys(searchRequest)
        val apiResponse = searchResponse.toApiResponse()

        logger.info("Found ${searchResponse.totalCount} journeys for search request")
        return ResponseEntity.ok(apiResponse)
    }
}