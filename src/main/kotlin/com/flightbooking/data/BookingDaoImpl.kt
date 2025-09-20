package com.flightbooking.data

import com.flightbooking.common.exception.DatabaseException
import com.flightbooking.data.mapper.BookingStatusConverter
import com.flightbooking.data.mapper.BookingDaoMappers
import com.flightbooking.domain.bookings.Booking
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.generated.jooq.tables.references.BOOKINGS
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.SQLException
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class BookingDaoImpl(
    private val dslContext: DSLContext,
    private val bookingMapper: BookingDaoMappers
) : BookingDao {

    private val logger = LoggerFactory.getLogger(BookingDaoImpl::class.java)
    private val bookingStatusConverter = BookingStatusConverter()

    override fun save(booking: Booking): Booking {
        return try {
            logger.debug("Saving booking with ID: ${booking.bookingId}")

            val result = dslContext.insertInto(BOOKINGS)
                .set(BOOKINGS.BOOKING_ID, booking.bookingId)
                .set(BOOKINGS.USER_ID, booking.userId)
                .set(BOOKINGS.JOURNEY_ID, booking.journeyId)
                .set(BOOKINGS.NUMBER_OF_SEATS, booking.numberOfSeats)
                .set(BOOKINGS.STATUS, bookingStatusConverter.to(booking.status))
                .set(BOOKINGS.PAYMENT_ID, booking.paymentId)
                .set(BOOKINGS.BOOKING_TIME, booking.bookingTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .returningResult(BOOKINGS.asterisk())
                .fetchOne()

            requireNotNull(result) { "Failed to insert booking - no result returned" }

            val savedBooking = bookingMapper.fromJooqRecord(result.into(BOOKINGS))
            logger.info("Successfully saved booking with ID: ${booking.bookingId}")
            savedBooking

        } catch (e: DataAccessException) {
            logger.error("Database access error while saving booking: ${e.message}", e)
            throw DatabaseException("Failed to save booking: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while saving booking: ${e.message}", e)
            throw DatabaseException("Database error occurred while saving booking: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while saving booking: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while saving booking: ${e.message}", e)
        }
    }

    override fun findById(bookingId: UUID): Booking? {
        return try {
            logger.debug("Finding booking by ID: $bookingId")

            val result = dslContext.selectFrom(BOOKINGS)
                .where(BOOKINGS.BOOKING_ID.eq(bookingId))
                .fetchOne()

            result?.let { bookingMapper.fromJooqRecord(it) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding booking by ID $bookingId: ${e.message}", e)
            throw DatabaseException("Failed to find booking: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding booking by ID $bookingId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding booking: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding booking by ID $bookingId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding booking: ${e.message}", e)
        }
    }

    override fun findByUserId(userId: UUID): List<Booking> {
        return try {
            logger.debug("Finding bookings for user ID: $userId")

            val results = dslContext.selectFrom(BOOKINGS)
                .where(BOOKINGS.USER_ID.eq(userId))
                .orderBy(BOOKINGS.BOOKING_TIME.desc())
                .fetch()

            results.map { record -> bookingMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding bookings for user $userId: ${e.message}", e)
            throw DatabaseException("Failed to find bookings: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding bookings for user $userId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding bookings: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding bookings for user $userId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding bookings: ${e.message}", e)
        }
    }

    override fun findByJourneyId(journeyId: UUID): List<Booking> {
        return try {
            logger.debug("Finding bookings for journey ID: $journeyId")

            val results = dslContext.selectFrom(BOOKINGS)
                .where(BOOKINGS.JOURNEY_ID.eq(journeyId))
                .orderBy(BOOKINGS.BOOKING_TIME.desc())
                .fetch()

            results.map { record -> bookingMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding bookings for journey $journeyId: ${e.message}", e)
            throw DatabaseException("Failed to find bookings: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding bookings for journey $journeyId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding bookings: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding bookings for journey $journeyId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding bookings: ${e.message}", e)
        }
    }

    override fun updateBookingStatus(bookingId: UUID, status: BookingStatus): Boolean {
        return try {
            logger.debug("Updating booking $bookingId status to $status")

            val updatedRows = dslContext.update(BOOKINGS)
                .set(BOOKINGS.STATUS, bookingStatusConverter.to(status))
                .set(BOOKINGS.UPDATED_AT, ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .where(BOOKINGS.BOOKING_ID.eq(bookingId))
                .execute()

            val success = updatedRows > 0
            logger.info("Booking $bookingId status update result: $success")
            success

        } catch (e: DataAccessException) {
            logger.error("Database access error while updating booking status: ${e.message}", e)
            throw DatabaseException("Failed to update booking status: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while updating booking status: ${e.message}", e)
            throw DatabaseException("Database error occurred while updating booking status: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while updating booking status: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while updating booking status: ${e.message}", e)
        }
    }

    override fun findByUserIdAndStatus(userId: UUID, status: BookingStatus): List<Booking> {
        return try {
            logger.debug("Finding bookings for user $userId with status $status")

            val results = dslContext.selectFrom(BOOKINGS)
                .where(BOOKINGS.USER_ID.eq(userId))
                .and(BOOKINGS.STATUS.eq(bookingStatusConverter.to(status)))
                .orderBy(BOOKINGS.BOOKING_TIME.desc())
                .fetch()

            results.map { record -> bookingMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding bookings for user $userId with status $status: ${e.message}", e)
            throw DatabaseException("Failed to find bookings: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding bookings for user $userId with status $status: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding bookings: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding bookings for user $userId with status $status: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding bookings: ${e.message}", e)
        }
    }

    override fun findAll(): List<Booking> {
        return try {
            logger.debug("Finding all bookings")

            val results = dslContext.selectFrom(BOOKINGS)
                .orderBy(BOOKINGS.BOOKING_TIME.desc())
                .fetch()

            results.map { record -> bookingMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding all bookings: ${e.message}", e)
            throw DatabaseException("Failed to find all bookings: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding all bookings: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding all bookings: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding all bookings: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding all bookings: ${e.message}", e)
        }
    }

    override fun deleteAll() {
        try {
            logger.debug("Deleting all bookings")
            val deletedRows = dslContext.deleteFrom(BOOKINGS).execute()
            logger.info("Deleted $deletedRows bookings")
        } catch (e: DataAccessException) {
            logger.error("Database access error while deleting all bookings: ${e.message}", e)
            throw DatabaseException("Failed to delete all bookings: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while deleting all bookings: ${e.message}", e)
            throw DatabaseException("Database error occurred while deleting all bookings: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while deleting all bookings: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while deleting all bookings: ${e.message}", e)
        }
    }
}