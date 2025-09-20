package com.flightbooking.data

import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.Journey
import java.time.LocalDate
import java.util.*

interface JourneyDao {
    fun save(journey: Journey): Journey
    fun findBySourceAndDestinationAndDate(source: String, destination: String, date: LocalDate): List<Journey>
    fun findFlightsByDate(date: LocalDate): List<Flight>
    fun findById(journeyId: UUID): Journey?
    fun count(): Long
    fun findAll(): List<Journey>
    fun deleteAll(): Int
}