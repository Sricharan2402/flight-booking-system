package com.flightbooking.data

import com.flightbooking.common.exception.DatabaseException
import com.flightbooking.data.mapper.SeatStatusConverter
import com.flightbooking.data.mapper.SeatDaoMappers
import com.flightbooking.domain.seats.Seat
import com.flightbooking.domain.common.SeatStatus
import com.flightbooking.generated.jooq.tables.references.SEATS
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLException
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class SeatDaoImpl(
    private val dslContext: DSLContext,
    private val seatMapper: SeatDaoMappers
) : SeatDao {

    private val logger = LoggerFactory.getLogger(SeatDaoImpl::class.java)
    private val seatStatusConverter = SeatStatusConverter()

    override fun save(seat: Seat): Seat {
        return try {
            logger.debug("Saving seat with ID: ${seat.seatId}")

            val result = dslContext.insertInto(SEATS)
                .set(SEATS.SEAT_ID, seat.seatId)
                .set(SEATS.FLIGHT_ID, seat.flightId)
                .set(SEATS.SEAT_NUMBER, seat.seatNumber)
                .set(SEATS.STATUS, seatStatusConverter.to(seat.status))
                .set(SEATS.BOOKING_ID, seat.bookingId)
                .returningResult(SEATS.asterisk())
                .fetchOne()

            requireNotNull(result) { "Failed to insert seat - no result returned" }

            val savedSeat = seatMapper.fromJooqRecord(result.into(SEATS))
            logger.info("Successfully saved seat with ID: ${seat.seatId}")
            savedSeat

        } catch (e: DataAccessException) {
            logger.error("Database access error while saving seat: ${e.message}", e)
            throw DatabaseException("Failed to save seat: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while saving seat: ${e.message}", e)
            throw DatabaseException("Database error occurred while saving seat: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while saving seat: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while saving seat: ${e.message}", e)
        }
    }

    @Transactional
    override fun saveAll(seats: List<Seat>): List<Seat> {
        return try {
            logger.debug("Saving ${seats.size} seats in batch")

            val savedSeats = mutableListOf<Seat>()

            seats.forEach { seat ->
                val result = dslContext.insertInto(SEATS)
                    .set(SEATS.SEAT_ID, seat.seatId)
                    .set(SEATS.FLIGHT_ID, seat.flightId)
                    .set(SEATS.SEAT_NUMBER, seat.seatNumber)
                    .set(SEATS.STATUS, seatStatusConverter.to(seat.status))
                    .set(SEATS.BOOKING_ID, seat.bookingId)
                    .returningResult(SEATS.asterisk())
                    .fetchOne()

                requireNotNull(result) { "Failed to insert seat ${seat.seatId} - no result returned" }
                savedSeats.add(seatMapper.fromJooqRecord(result.into(SEATS)))
            }

            logger.info("Successfully saved ${savedSeats.size} seats in batch")
            savedSeats

        } catch (e: DataAccessException) {
            logger.error("Database access error while saving seats in batch: ${e.message}", e)
            throw DatabaseException("Failed to save seats in batch: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while saving seats in batch: ${e.message}", e)
            throw DatabaseException("Database error occurred while saving seats in batch: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while saving seats in batch: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while saving seats in batch: ${e.message}", e)
        }
    }

    override fun findById(seatId: UUID): Seat? {
        return try {
            logger.debug("Finding seat by ID: $seatId")

            val result = dslContext.selectFrom(SEATS)
                .where(SEATS.SEAT_ID.eq(seatId))
                .fetchOne()

            result?.let { seatMapper.fromJooqRecord(it) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding seat by ID $seatId: ${e.message}", e)
            throw DatabaseException("Failed to find seat: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding seat by ID $seatId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding seat: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding seat by ID $seatId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding seat: ${e.message}", e)
        }
    }

    override fun findByFlightId(flightId: UUID): List<Seat> {
        return try {
            logger.debug("Finding seats for flight ID: $flightId")

            val results = dslContext.selectFrom(SEATS)
                .where(SEATS.FLIGHT_ID.eq(flightId))
                .orderBy(SEATS.SEAT_NUMBER)
                .fetch()

            results.map { record -> seatMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Failed to find seats: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding seats: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding seats: ${e.message}", e)
        }
    }

    override fun findAvailableSeatsByFlightId(flightId: UUID): List<Seat> {
        return try {
            logger.debug("Finding available seats for flight ID: $flightId")

            val results = dslContext.selectFrom(SEATS)
                .where(SEATS.FLIGHT_ID.eq(flightId))
                .and(SEATS.STATUS.eq(seatStatusConverter.to(SeatStatus.AVAILABLE)))
                .orderBy(SEATS.SEAT_NUMBER)
                .fetch()

            results.map { record -> seatMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding available seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Failed to find available seats: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding available seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding available seats: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding available seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding available seats: ${e.message}", e)
        }
    }

    override fun countAvailableSeatsByFlightId(flightId: UUID): Int {
        return try {
            logger.debug("Counting available seats for flight ID: $flightId")

            val count = dslContext.selectCount()
                .from(SEATS)
                .where(SEATS.FLIGHT_ID.eq(flightId))
                .and(SEATS.STATUS.eq(seatStatusConverter.to(SeatStatus.AVAILABLE)))
                .fetchOne(0, Int::class.java) ?: 0

            logger.debug("Found $count available seats for flight $flightId")
            count

        } catch (e: DataAccessException) {
            logger.error("Database access error while counting available seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Failed to count available seats: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while counting available seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Database error occurred while counting available seats: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while counting available seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while counting available seats: ${e.message}", e)
        }
    }

    override fun updateSeatStatus(seatId: UUID, status: SeatStatus, bookingId: UUID?): Boolean {
        return try {
            logger.debug("Updating seat $seatId status to $status")

            val updatedRows = dslContext.update(SEATS)
                .set(SEATS.STATUS, seatStatusConverter.to(status))
                .set(SEATS.BOOKING_ID, bookingId)
                .set(SEATS.UPDATED_AT, ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .where(SEATS.SEAT_ID.eq(seatId))
                .execute()

            val success = updatedRows > 0
            logger.info("Seat $seatId status update result: $success")
            success

        } catch (e: DataAccessException) {
            logger.error("Database access error while updating seat status: ${e.message}", e)
            throw DatabaseException("Failed to update seat status: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while updating seat status: ${e.message}", e)
            throw DatabaseException("Database error occurred while updating seat status: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while updating seat status: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while updating seat status: ${e.message}", e)
        }
    }

    override fun findByBookingId(bookingId: UUID): List<Seat> {
        return try {
            logger.debug("Finding seats for booking ID: $bookingId")

            val results = dslContext.selectFrom(SEATS)
                .where(SEATS.BOOKING_ID.eq(bookingId))
                .orderBy(SEATS.SEAT_NUMBER)
                .fetch()

            results.map { record -> seatMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding seats for booking $bookingId: ${e.message}", e)
            throw DatabaseException("Failed to find seats for booking: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding seats for booking $bookingId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding seats for booking: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding seats for booking $bookingId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding seats for booking: ${e.message}", e)
        }
    }

    override fun createSeatsForFlight(flightId: UUID, totalSeats: Int): List<Seat> {
        return try {
            logger.info("Creating $totalSeats seats for flight ID: $flightId")

            val seats = mutableListOf<Seat>()
            val now = ZonedDateTime.now()

            for (i in 1..totalSeats) {
                val seatNumber = generateSeatNumber(i)
                val seat = Seat(
                    seatId = UUID.randomUUID(),
                    flightId = flightId,
                    seatNumber = seatNumber,
                    status = SeatStatus.AVAILABLE,
                    bookingId = null,
                    createdAt = now,
                    updatedAt = now
                )
                seats.add(seat)
            }

            // Use saveAll for better atomicity
            val savedSeats = saveAll(seats)
            logger.info("Successfully created ${savedSeats.size} seats for flight $flightId")
            savedSeats

        } catch (e: Exception) {
            logger.error("Error while creating seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Failed to create seats for flight: ${e.message}", e)
        }
    }

    override fun updateSeatsForBooking(seatIds: List<UUID>, bookingId: UUID, status: SeatStatus): Boolean {
        return try {
            logger.debug("Updating ${seatIds.size} seats for booking $bookingId to status $status")

            val updatedRows = dslContext.update(SEATS)
                .set(SEATS.STATUS, seatStatusConverter.to(status))
                .set(SEATS.BOOKING_ID, bookingId)
                .set(SEATS.UPDATED_AT, ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .where(SEATS.SEAT_ID.`in`(seatIds))
                .execute()

            val success = updatedRows == seatIds.size
            logger.info("Updated $updatedRows seats for booking $bookingId (expected ${seatIds.size})")
            success

        } catch (e: DataAccessException) {
            logger.error("Database access error while updating seats for booking: ${e.message}", e)
            throw DatabaseException("Failed to update seats for booking: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while updating seats for booking: ${e.message}", e)
            throw DatabaseException("Database error occurred while updating seats for booking: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while updating seats for booking: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while updating seats for booking: ${e.message}", e)
        }
    }

    private fun generateSeatNumber(index: Int): String {
        val rowNumber = ((index - 1) / 6) + 1
        val seatLetter = when ((index - 1) % 6) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            3 -> "D"
            4 -> "E"
            5 -> "F"
            else -> "A"
        }
        return "$rowNumber$seatLetter"
    }

    override fun findAvailableSeatsByFlight(flightId: UUID): List<Seat> {
        return findAvailableSeatsByFlightId(flightId)
    }

    override fun findBookedSeatsByFlight(flightId: UUID): List<Seat> {
        return try {
            logger.debug("Finding booked seats for flight ID: $flightId")

            val results = dslContext.selectFrom(SEATS)
                .where(SEATS.FLIGHT_ID.eq(flightId))
                .and(SEATS.STATUS.eq(seatStatusConverter.to(SeatStatus.BOOKED)))
                .orderBy(SEATS.SEAT_NUMBER)
                .fetch()

            results.map { record -> seatMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding booked seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Failed to find booked seats: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding booked seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding booked seats: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding booked seats for flight $flightId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding booked seats: ${e.message}", e)
        }
    }

    override fun create(flightId: UUID, seatNumber: String, seatClass: String, price: java.math.BigDecimal, isAvailable: Boolean): Seat {
        val seat = Seat(
            seatId = UUID.randomUUID(),
            flightId = flightId,
            seatNumber = seatNumber,
            status = if (isAvailable) SeatStatus.AVAILABLE else SeatStatus.BLOCKED,
            bookingId = null,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
        return save(seat)
    }

    override fun deleteAll() {
        try {
            logger.debug("Deleting all seats")
            val deletedRows = dslContext.deleteFrom(SEATS).execute()
            logger.info("Deleted $deletedRows seats")
        } catch (e: DataAccessException) {
            logger.error("Database access error while deleting all seats: ${e.message}", e)
            throw DatabaseException("Failed to delete all seats: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while deleting all seats: ${e.message}", e)
            throw DatabaseException("Database error occurred while deleting all seats: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while deleting all seats: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while deleting all seats: ${e.message}", e)
        }
    }
}