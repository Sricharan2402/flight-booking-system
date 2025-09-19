package com.flightbooking.data.mapper

import com.flightbooking.domain.Flight
import com.flightbooking.generated.jooq.tables.records.FlightsRecord
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Component
class FlightDaoMappers {

    fun fromJooqRecord(record: FlightsRecord): Flight {
        return Flight(
            flightId = record.flightId!!,
            sourceAirport = record.sourceAirport!!,
            destinationAirport = record.destinationAirport!!,
            departureTime = toZonedDateTime(record.departureTime!!),
            arrivalTime = toZonedDateTime(record.arrivalTime!!),
            airplaneId = record.airplaneId!!,
            price = record.price!!,
            status = record.status!!,
            createdAt = toZonedDateTime(record.createdAt!!),
            updatedAt = toZonedDateTime(record.updatedAt!!)
        )
    }

    private fun toZonedDateTime(localDateTime: LocalDateTime): ZonedDateTime {
        return localDateTime.atZone(ZoneOffset.UTC)
    }
}