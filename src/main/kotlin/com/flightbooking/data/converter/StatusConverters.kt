package com.flightbooking.data.converter

import com.flightbooking.domain.common.FlightStatus
import com.flightbooking.domain.common.JourneyStatus
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.common.SeatStatus
import org.jooq.Converter

class FlightStatusConverter : Converter<String, FlightStatus> {
    override fun from(databaseObject: String?): FlightStatus? {
        return databaseObject?.let { FlightStatus.valueOf(it) }
    }

    override fun to(userObject: FlightStatus?): String? {
        return userObject?.name
    }

    override fun fromType(): Class<String> = String::class.java
    override fun toType(): Class<FlightStatus> = FlightStatus::class.java
}

class JourneyStatusConverter : Converter<String, JourneyStatus> {
    override fun from(databaseObject: String?): JourneyStatus? {
        return databaseObject?.let { JourneyStatus.valueOf(it) }
    }

    override fun to(userObject: JourneyStatus?): String? {
        return userObject?.name
    }

    override fun fromType(): Class<String> = String::class.java
    override fun toType(): Class<JourneyStatus> = JourneyStatus::class.java
}

class BookingStatusConverter : Converter<String, BookingStatus> {
    override fun from(databaseObject: String?): BookingStatus? {
        return databaseObject?.let { BookingStatus.valueOf(it) }
    }

    override fun to(userObject: BookingStatus?): String? {
        return userObject?.name
    }

    override fun fromType(): Class<String> = String::class.java
    override fun toType(): Class<BookingStatus> = BookingStatus::class.java
}

class SeatStatusConverter : Converter<String, SeatStatus> {
    override fun from(databaseObject: String?): SeatStatus? {
        return databaseObject?.let { SeatStatus.valueOf(it) }
    }

    override fun to(userObject: SeatStatus?): String? {
        return userObject?.name
    }

    override fun fromType(): Class<String> = String::class.java
    override fun toType(): Class<SeatStatus> = SeatStatus::class.java
}