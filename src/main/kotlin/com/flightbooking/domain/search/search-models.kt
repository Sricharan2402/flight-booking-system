package com.flightbooking.domain.search

import com.flightbooking.domain.journeys.Journey
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
    val journeys: List<Journey>,
    val totalCount: Int
)