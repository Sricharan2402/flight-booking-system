package com.flightbooking.data.mapper

import com.flightbooking.domain.bookings.Booking
import com.flightbooking.generated.jooq.tables.records.BookingsRecord
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class BookingDaoMappers {

    private val bookingStatusConverter = BookingStatusConverter()

    fun fromJooqRecord(record: BookingsRecord): Booking {
        return Booking(
            bookingId = record.bookingId!!,
            userId = record.userId!!,
            journeyId = record.journeyId!!,
            numberOfSeats = record.numberOfSeats!!,
            status = bookingStatusConverter.from(record.status) ?: throw IllegalStateException("Invalid booking status: ${record.status}"),
            paymentId = record.paymentId ?: "",
            bookingTime = record.bookingTime!!.atZone(ZoneOffset.UTC),
            createdAt = record.createdAt!!.atZone(ZoneOffset.UTC),
            updatedAt = record.updatedAt!!.atZone(ZoneOffset.UTC)
        )
    }

    fun toJooqRecord(booking: Booking): BookingsRecord {
        val record = BookingsRecord()
        record.bookingId = booking.bookingId
        record.userId = booking.userId
        record.journeyId = booking.journeyId
        record.numberOfSeats = booking.numberOfSeats
        record.status = bookingStatusConverter.to(booking.status)
        record.paymentId = booking.paymentId
        record.bookingTime = booking.bookingTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        record.createdAt = booking.createdAt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        record.updatedAt = booking.updatedAt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        return record
    }
}