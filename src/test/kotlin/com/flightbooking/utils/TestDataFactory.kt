package com.flightbooking.utils

import com.flightbooking.domain.bookings.Booking
import com.flightbooking.domain.bookings.BookingRequest
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.common.FlightStatus
import com.flightbooking.domain.common.JourneyStatus
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.domain.journeys.FlightReference
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.domain.seats.Seat
import com.flightbooking.domain.common.SeatStatus
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

object TestDataFactory {

    fun createTestFlight(
        flightId: UUID = UUID.randomUUID(),
        sourceAirport: String = "DEL",
        destinationAirport: String = "BOM",
        departureTime: ZonedDateTime = ZonedDateTime.now().plusHours(2),
        arrivalTime: ZonedDateTime = ZonedDateTime.now().plusHours(4),
        airplaneId: UUID = UUID.randomUUID(),
        price: BigDecimal = BigDecimal("5000.00"),
        status: FlightStatus = FlightStatus.ACTIVE
    ): Flight {
        return Flight(
            flightId = flightId,
            sourceAirport = sourceAirport,
            destinationAirport = destinationAirport,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            airplaneId = airplaneId,
            price = price,
            status = status,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
    }

    fun createTestJourney(
        journeyId: UUID = UUID.randomUUID(),
        flightReferences: List<FlightReference> = listOf(
            FlightReference(UUID.randomUUID(), 1)
        ),
        sourceAirport: String = "DEL",
        destinationAirport: String = "BOM",
        departureTime: ZonedDateTime = ZonedDateTime.now().plusHours(2),
        arrivalTime: ZonedDateTime = ZonedDateTime.now().plusHours(4),
        totalPrice: BigDecimal = BigDecimal("5000.00"),
        status: JourneyStatus = JourneyStatus.ACTIVE
    ): Journey {
        return Journey(
            journeyId = journeyId,
            flightDetails = flightReferences,
            sourceAirport = sourceAirport,
            destinationAirport = destinationAirport,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            totalPrice = totalPrice,
            status = status,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
    }

    fun createTestBookingRequest(
        journeyId: UUID = UUID.randomUUID(),
        passengerCount: Int = 2,
        paymentId: String = "payment_${UUID.randomUUID()}",
        userId: UUID = UUID.randomUUID()
    ): BookingRequest {
        return BookingRequest(
            journeyId = journeyId,
            passengerCount = passengerCount,
            paymentId = paymentId,
            userId = userId
        )
    }

    fun createTestBooking(
        bookingId: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        journeyId: UUID = UUID.randomUUID(),
        numberOfSeats: Int = 2,
        status: BookingStatus = BookingStatus.CONFIRMED,
        paymentId: String = "payment_${UUID.randomUUID()}"
    ): Booking {
        return Booking(
            bookingId = bookingId,
            userId = userId,
            journeyId = journeyId,
            numberOfSeats = numberOfSeats,
            status = status,
            paymentId = paymentId,
            bookingTime = ZonedDateTime.now(),
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
    }

    fun createTestSeat(
        seatId: UUID = UUID.randomUUID(),
        flightId: UUID = UUID.randomUUID(),
        seatNumber: String = "1A",
        status: SeatStatus = SeatStatus.AVAILABLE,
        bookingId: UUID? = null
    ): Seat {
        return Seat(
            seatId = seatId,
            flightId = flightId,
            seatNumber = seatNumber,
            status = status,
            bookingId = bookingId,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
    }

    fun createTestFlightCreationRequest(
        sourceAirport: String = "DEL",
        destinationAirport: String = "BOM",
        departureTime: ZonedDateTime = ZonedDateTime.now().plusHours(2),
        arrivalTime: ZonedDateTime = ZonedDateTime.now().plusHours(4),
        airplaneId: UUID = UUID.randomUUID(),
        price: BigDecimal = BigDecimal("5000.00"),
        totalSeats: Int = 180
    ): FlightCreationRequest {
        return FlightCreationRequest(
            sourceAirport = sourceAirport,
            destinationAirport = destinationAirport,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            airplaneId = airplaneId,
            price = price,
            totalSeats = totalSeats
        )
    }

    // Helper methods for creating multiple objects
    fun createTestFlights(count: Int, baseSourceAirport: String = "DEL"): List<Flight> {
        return (1..count).map { index ->
            createTestFlight(
                sourceAirport = baseSourceAirport,
                destinationAirport = "BOM$index",
                departureTime = ZonedDateTime.now().plusHours(index.toLong()),
                arrivalTime = ZonedDateTime.now().plusHours(index.toLong() + 2)
            )
        }
    }

    fun createTestSeats(flightId: UUID, count: Int): List<Seat> {
        return (1..count).map { index ->
            val row = ((index - 1) / 6) + 1
            val seat = 'A' + ((index - 1) % 6)
            createTestSeat(
                flightId = flightId,
                seatNumber = "$row$seat"
            )
        }
    }

    // Helper for creating connected flights (valid layovers)
    fun createConnectedFlights(
        firstSource: String = "DEL",
        connectingAirport: String = "BOM",
        finalDestination: String = "MAA"
    ): Pair<Flight, Flight> {
        val firstFlight = createTestFlight(
            sourceAirport = firstSource,
            destinationAirport = connectingAirport,
            departureTime = ZonedDateTime.now().plusHours(2),
            arrivalTime = ZonedDateTime.now().plusHours(4)
        )

        val secondFlight = createTestFlight(
            sourceAirport = connectingAirport,
            destinationAirport = finalDestination,
            departureTime = ZonedDateTime.now().plusHours(5), // 1 hour layover
            arrivalTime = ZonedDateTime.now().plusHours(7)
        )

        return Pair(firstFlight, secondFlight)
    }
}