package com.flightbooking.data

import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.Journey
import java.time.LocalDate
import java.util.*

interface JourneyDao {
    suspend fun save(journey: Journey): Journey
    suspend fun findBySourceAndDestinationAndDate(source: String, destination: String, date: LocalDate): List<Journey>
    suspend fun findFlightsByDate(date: LocalDate): List<Flight>
    suspend fun findById(journeyId: UUID): Journey?
}