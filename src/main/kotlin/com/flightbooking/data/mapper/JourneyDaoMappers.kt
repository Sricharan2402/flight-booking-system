package com.flightbooking.data.mapper

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.flightbooking.domain.journeys.FlightReference
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.generated.jooq.tables.records.JourneysRecord
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Component
class JourneyDaoMappers {

    private val objectMapper = ObjectMapper()
    private val journeyStatusConverter = JourneyStatusConverter()

    fun fromJooqRecord(record: JourneysRecord): Journey {
        return Journey(
            journeyId = record.journeyId!!,
            flightDetails = parseFlightDetails(record.flightDetails!!.data()),
            sourceAirport = record.sourceAirport!!,
            destinationAirport = record.destinationAirport!!,
            departureTime = toZonedDateTime(record.departureTime!!),
            arrivalTime = toZonedDateTime(record.arrivalTime!!),
            totalPrice = record.totalPrice!!,
            status = journeyStatusConverter.from(record.status!!)!!,
            createdAt = toZonedDateTime(record.createdAt!!),
            updatedAt = toZonedDateTime(record.updatedAt!!)
        )
    }

    private fun parseFlightDetails(jsonData: String): List<FlightReference> {
        val typeRef = object : TypeReference<Map<String, List<Map<String, Any>>>>() {}
        val jsonMap = objectMapper.readValue(jsonData, typeRef)
        val flightsList = jsonMap["flights"] ?: emptyList()

        return flightsList.map { flightMap ->
            FlightReference(
                flightId = java.util.UUID.fromString(flightMap["id"] as String),
                order = (flightMap["order"] as Number).toInt()
            )
        }
    }

    fun toFlightDetailsJson(flightDetails: List<FlightReference>): String {
        val flightMap = mapOf(
            "flights" to flightDetails.map { ref ->
                mapOf(
                    "id" to ref.flightId.toString(),
                    "order" to ref.order
                )
            }
        )
        return objectMapper.writeValueAsString(flightMap)
    }

    private fun toZonedDateTime(localDateTime: LocalDateTime): ZonedDateTime {
        return localDateTime.atZone(ZoneOffset.UTC)
    }
}