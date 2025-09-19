package com.flightbooking.data

import com.flightbooking.domain.Flight
import com.flightbooking.domain.FlightCreationRequest
import java.util.*

interface FlightDao {

    suspend fun save(flightCreationRequest: FlightCreationRequest): Flight

    suspend fun findById(flightId: UUID): Flight?
}