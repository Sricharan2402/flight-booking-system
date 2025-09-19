package com.flightbooking.controller.mapper

import com.flightbooking.domain.search.FlightSearchResult
import com.flightbooking.generated.search.model.FlightReference
import com.flightbooking.domain.bookings.BookingRequest as DomainBookingRequest
import com.flightbooking.domain.bookings.BookingResponse as DomainBookingResponse
import com.flightbooking.domain.bookings.SeatAssignment as DomainSeatAssignment
import com.flightbooking.domain.bookings.JourneyDetails as DomainJourneyDetails
import com.flightbooking.domain.journeys.FlightReference as DomainFlightReference
import com.flightbooking.generated.booking.model.CreateBookingRequest as ApiCreateBookingRequest
import com.flightbooking.generated.booking.model.BookingResponse as ApiBookingResponse
import com.flightbooking.generated.booking.model.SeatAssignment as ApiSeatAssignment
import com.flightbooking.generated.booking.model.JourneyDetails as ApiJourneyDetails
import com.flightbooking.generated.booking.model.FlightReference as ApiFlightReference
import java.time.ZoneOffset
import java.util.*

fun ApiCreateBookingRequest.toServiceModel(): DomainBookingRequest {
    return DomainBookingRequest(
        journeyId = this.journeyId,
        passengerCount = this.passengerCount,
        paymentId = this.paymentId,
        userId = UUID.randomUUID() // TODO: Extract from authentication context
    )
}

fun DomainBookingResponse.toApiResponse(): ApiBookingResponse {
    return ApiBookingResponse(
        id = this.id,
        journeyId = this.journeyId,
        passengerCount = this.passengerCount,
        status = ApiBookingResponse.Status.valueOf(this.status.uppercase()),
        paymentId = this.paymentId,
        seatAssignments = this.seatAssignments.map { it.toApiSeatAssignment() },
        journeyDetails = this.journeyDetails.toApiJourneyDetails(),
        createdAt = this.createdAt.toOffsetDateTime(),
        updatedAt = this.updatedAt.toOffsetDateTime()
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
        departureTime = this.departureTime.toOffsetDateTime(),
        arrivalTime = this.arrivalTime.toOffsetDateTime(),
        layoverCount = this.layoverCount,
        flights = this.flights.mapIndexed { index, flight -> flight.toApiFlight(index + 1) },
    )
}

fun DomainFlightReference.toApiFlight(order: Int): ApiFlightReference {
    return ApiFlightReference(
        id = flightId,
        order = order
    )
}