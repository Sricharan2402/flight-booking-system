package com.flightbooking.controller.mapper

import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.domain.flights.FlightCreationResponse
import com.flightbooking.generated.admin.model.CreateFlightRequest
import com.flightbooking.generated.admin.model.FlightResponse
import java.time.ZoneOffset

fun CreateFlightRequest.toServiceModel(): FlightCreationRequest {
    return FlightCreationRequest(
        sourceAirport = this.sourceAirport,
        destinationAirport = this.destinationAirport,
        departureTime = this.departureTime.atZoneSameInstant(ZoneOffset.UTC),
        arrivalTime = this.arrivalTime.atZoneSameInstant(ZoneOffset.UTC),
        airplaneId = this.airplaneId,
        price = this.price,
        totalSeats = this.totalSeats
    )
}

fun FlightCreationResponse.toApiResponse(): FlightResponse {
    return FlightResponse(
        id = this.flightId,
        sourceAirport = this.sourceAirport,
        destinationAirport = this.destinationAirport,
        departureTime = this.departureTime.toOffsetDateTime(),
        arrivalTime = this.arrivalTime.toOffsetDateTime(),
        airplaneId = this.airplaneId,
        price = this.price,
        totalSeats = this.totalSeats,
        availableSeats = this.availableSeats,
        createdAt = this.createdAt.toOffsetDateTime(),
        updatedAt = this.updatedAt.toOffsetDateTime()
    )
}

