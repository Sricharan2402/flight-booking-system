package com.flightbooking.controller.mapper

import com.flightbooking.domain.search.SearchResponse as DomainSearchResponse
import com.flightbooking.domain.search.JourneySearchResult as DomainJourneySearchResult
import com.flightbooking.domain.search.FlightSearchResult as DomainFlightSearchResult
import com.flightbooking.generated.search.model.SearchResponse as ApiSearchResponse
import com.flightbooking.generated.search.model.Journey as ApiJourney
import com.flightbooking.generated.search.model.FlightReference as ApiFlight
import java.math.BigDecimal

fun DomainSearchResponse.toApiResponse(): ApiSearchResponse {
    return ApiSearchResponse(
        journeys = this.journeys.map { it.toApiJourney() },
        totalCount = this.totalCount
    )
}

fun DomainJourneySearchResult.toApiJourney(): ApiJourney {
    return ApiJourney(
        id = java.util.UUID.fromString(this.id),
        departureTime = java.time.OffsetDateTime.parse(this.departureTime),
        arrivalTime = java.time.OffsetDateTime.parse(this.arrivalTime),
        totalPrice = BigDecimal(this.totalPrice),
        layoverCount = this.layoverCount,
        flights = this.flights.mapIndexed { index, flight -> flight.toApiFlight(index + 1) },
        availableSeats = this.availableSeats
    )
}

fun DomainFlightSearchResult.toApiFlight(order: Int): ApiFlight {
    return ApiFlight(
        id = java.util.UUID.fromString(this.id),
        order = order
    )
}