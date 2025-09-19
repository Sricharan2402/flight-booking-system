package com.flightbooking.data.mapper

import com.flightbooking.domain.seats.Seat
import com.flightbooking.generated.jooq.tables.records.SeatsRecord
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class SeatDaoMappers {

    private val seatStatusConverter = SeatStatusConverter()

    fun fromJooqRecord(record: SeatsRecord): Seat {
        return Seat(
            seatId = record.seatId!!,
            flightId = record.flightId!!,
            seatNumber = record.seatNumber!!,
            status = seatStatusConverter.from(record.status) ?: throw IllegalStateException("Invalid seat status: ${record.status}"),
            bookingId = record.bookingId,
            createdAt = record.createdAt!!.atZone(ZoneOffset.UTC),
            updatedAt = record.updatedAt!!.atZone(ZoneOffset.UTC)
        )
    }

    fun toJooqRecord(seat: Seat): SeatsRecord {
        val record = SeatsRecord()
        record.seatId = seat.seatId
        record.flightId = seat.flightId
        record.seatNumber = seat.seatNumber
        record.status = seatStatusConverter.to(seat.status)
        record.bookingId = seat.bookingId
        record.createdAt = seat.createdAt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        record.updatedAt = seat.updatedAt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        return record
    }
}