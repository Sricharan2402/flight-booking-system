package com.flightbooking.controller.mapper

import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.domain.flights.FlightCreationResponse
import com.flightbooking.generated.server.model.CreateFlightRequest
import com.flightbooking.generated.server.model.FlightResponse
import java.time.ZoneOffset

fun CreateFlightRequest.toServiceModel(): FlightCreationRequest {
    return FlightCreationRequest(
        sourceAirport = this.sourceAirport,
        destinationAirport = this.destinationAirport,
        departureTime = this.departureTime,
        arrivalTime = this.arrivalTime,
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
        departureTime = this.departureTime,
        arrivalTime = this.arrivalTime,
        airplaneId = this.airplaneId,
        price = this.price,
        totalSeats = this.totalSeats,
        availableSeats = this.availableSeats,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}