package com.flightbooking.controller.mapper

import com.flightbooking.domain.FlightCreationRequest
import com.flightbooking.domain.FlightCreationResponse
import com.flightbooking.generated.admin.model.CreateFlightRequest
import com.flightbooking.generated.admin.model.FlightResponse

fun CreateFlightRequest.toServiceModel(): FlightCreationRequest {
    return FlightCreationRequest(
        sourceAirport = this.sourceAirport,
        destinationAirport = this.destinationAirport,
        departureTime = this.departureTime.toZonedDateTime(),
        arrivalTime = this.arrivalTime.toZonedDateTime(),
        airplaneId = this.airplaneId,
        price = this.price
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
        createdAt = this.createdAt.toOffsetDateTime(),
        updatedAt = this.updatedAt.toOffsetDateTime()
    )
}