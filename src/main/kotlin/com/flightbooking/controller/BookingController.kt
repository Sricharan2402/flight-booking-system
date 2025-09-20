package com.flightbooking.controller

import com.flightbooking.controller.mapper.toApiResponse
import com.flightbooking.controller.mapper.toServiceModel
import com.flightbooking.generated.server.api.BookingsApi
import com.flightbooking.generated.server.model.CreateBookingRequest
import com.flightbooking.generated.server.model.BookingResponse
import com.flightbooking.services.booking.BookingService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class BookingController(
    private val bookingService: BookingService
) : BookingsApi {

    private val logger = LoggerFactory.getLogger(BookingController::class.java)

    override fun createBooking(
        userId: String,
        createBookingRequest: CreateBookingRequest
    ): ResponseEntity<BookingResponse> {
        logger.info("Creating booking for journey ${createBookingRequest.journeyId}")

        val bookingRequest = createBookingRequest.toServiceModel(userId)
        val bookingResponse = bookingService.createBooking(bookingRequest)
        val apiResponse = bookingResponse.toApiResponse()

        logger.info("Successfully created booking with ID: ${bookingResponse.id}")
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse)
    }

    override fun getBooking(bookingId: UUID): ResponseEntity<BookingResponse> {
        logger.info("Retrieving booking with ID: $bookingId")

        val bookingResponse = bookingService.getBooking(bookingId)
        val apiResponse = bookingResponse.toApiResponse()

        logger.info("Successfully retrieved booking with ID: $bookingId")
        return ResponseEntity.ok(apiResponse)
    }
}