package com.flightbooking.controller

import com.flightbooking.controller.mapper.toApiResponse
import com.flightbooking.controller.mapper.toServiceModel
import com.flightbooking.generated.server.api.FlightsApi
import com.flightbooking.generated.server.model.CreateFlightRequest
import com.flightbooking.generated.server.model.FlightResponse
import com.flightbooking.services.admin.AdminFlightService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminFlightController(
    private val adminFlightService: AdminFlightService
): FlightsApi {

    private val logger = LoggerFactory.getLogger(AdminFlightController::class.java)

    override fun createFlight(createFlightRequest: CreateFlightRequest): ResponseEntity<FlightResponse> {
        logger.info("Received flight creation request")

        // Map request DTO to service model
        val serviceRequest = createFlightRequest.toServiceModel()

        // Call service layer
        val serviceResponse = adminFlightService.createFlight(serviceRequest)

        // Map service response to API response
        val apiResponse = serviceResponse.toApiResponse()

        logger.info("Successfully created flight with ID: ${serviceResponse.flightId}")
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse)
    }
}