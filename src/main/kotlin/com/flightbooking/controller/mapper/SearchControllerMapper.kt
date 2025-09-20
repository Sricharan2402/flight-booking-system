package com.flightbooking.controller.mapper

import com.flightbooking.domain.search.SearchResponse as DomainSearchResponse
import com.flightbooking.domain.journeys.Journey as DomainJourney
import com.flightbooking.domain.journeys.FlightReference as DomainFlightReference
import com.flightbooking.generated.server.model.SearchResponse as ApiSearchResponse
import com.flightbooking.generated.server.model.Journey as ApiJourney
import com.flightbooking.generated.server.model.FlightReference as ApiFlight
import java.time.ZoneOffset

fun DomainSearchResponse.toApiResponse(seatCounts: Map<java.util.UUID, Int>): ApiSearchResponse {
    return ApiSearchResponse(
        journeys = this.journeys.map { it.toApiJourney(seatCounts) },
        totalCount = this.totalCount
    )
}

fun DomainJourney.toApiJourney(seatCounts: Map<java.util.UUID, Int>): ApiJourney {
    return ApiJourney(
        id = this.journeyId,
        departureTime = this.departureTime.toOffsetDateTime(),
        arrivalTime = this.arrivalTime.toOffsetDateTime(),
        totalPrice = this.totalPrice,
        layoverCount = this.flightDetails.size - 1,
        flights = this.flightDetails.map { it.toApiFlight() },
        availableSeats = seatCounts[this.journeyId] ?: 0
    )
}

fun DomainFlightReference.toApiFlight(): ApiFlight {
    return ApiFlight(
        id = this.flightId,
        order = this.order
    )
}