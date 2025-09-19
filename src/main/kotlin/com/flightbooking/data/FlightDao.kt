package com.flightbooking.data

import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.flights.FlightCreationRequest
import java.util.*

interface FlightDao {

    fun save(flightCreationRequest: FlightCreationRequest): Flight

    fun findById(flightId: UUID): Flight?
}