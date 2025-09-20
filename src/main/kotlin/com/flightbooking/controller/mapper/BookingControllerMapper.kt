package com.flightbooking.controller.mapper

import com.flightbooking.domain.bookings.BookingRequest as DomainBookingRequest
import com.flightbooking.domain.bookings.BookingResponse as DomainBookingResponse
import com.flightbooking.domain.bookings.SeatAssignment as DomainSeatAssignment
import com.flightbooking.domain.bookings.JourneyDetails as DomainJourneyDetails
import com.flightbooking.domain.journeys.FlightReference as DomainFlightReference
import com.flightbooking.generated.server.model.CreateBookingRequest as ApiCreateBookingRequest
import com.flightbooking.generated.server.model.BookingResponse as ApiBookingResponse
import com.flightbooking.generated.server.model.SeatAssignment as ApiSeatAssignment
import com.flightbooking.generated.server.model.JourneyDetails as ApiJourneyDetails
import com.flightbooking.generated.server.model.FlightReference as ApiFlightReference
import java.util.*

fun ApiCreateBookingRequest.toServiceModel(userId: String): DomainBookingRequest {
    return DomainBookingRequest(
        journeyId = this.journeyId,
        passengerCount = this.passengerCount,
        paymentId = this.paymentId,
        userId = UUID.fromString(userId)
    )
}

fun DomainBookingResponse.toApiResponse(): ApiBookingResponse {
    return ApiBookingResponse(
        id = this.id,
        journeyId = this.journeyId,
        passengerCount = this.passengerCount,
        status = ApiBookingResponse.Status.valueOf(this.status.name.uppercase()),
        paymentId = this.paymentId,
        seatAssignments = this.seatAssignments.map { it.toApiSeatAssignment() },
        journeyDetails = this.journeyDetails.toApiJourneyDetails(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
}

fun DomainSeatAssignment.toApiSeatAssignment(): ApiSeatAssignment {
    return ApiSeatAssignment(
        flightId = this.flightId,
        seatNumbers = this.seatNumbers
    )
}

fun DomainJourneyDetails.toApiJourneyDetails(): ApiJourneyDetails {
    return ApiJourneyDetails(
        id = this.id,
        departureTime = this.departureTime,
        arrivalTime = this.arrivalTime,
        layoverCount = this.layoverCount,
        flights = this.flights.map{ it.toApiFlight() },
    )
}