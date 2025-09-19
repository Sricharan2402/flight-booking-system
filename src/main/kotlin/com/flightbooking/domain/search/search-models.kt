package com.flightbooking.domain.search

import java.time.LocalDate

data class SearchRequest(
    val sourceAirport: String,
    val destinationAirport: String,
    val departureDate: LocalDate,
    val passengers: Int,
    val sortBy: String? = null,
    val limit: Int? = null
)

data class SearchResponse(
    val journeys: List<JourneySearchResult>,
    val totalCount: Int
)

data class JourneySearchResult(
    val id: String,
    val departureTime: String,
    val arrivalTime: String,
    val totalPrice: String,
    val layoverCount: Int,
    val flights: List<FlightSearchResult>,
    val availableSeats: Int
)

data class FlightSearchResult(
    val id: String,
    val flightNumber: String,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: String,
    val arrivalTime: String,
    val price: String
)