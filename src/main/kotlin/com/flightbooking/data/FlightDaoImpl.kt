package com.flightbooking.data

import com.flightbooking.data.mapper.FlightDaoMappers
import com.flightbooking.data.converter.FlightStatusConverter
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.domain.common.FlightStatus
import com.flightbooking.common.exception.DatabaseException
import com.flightbooking.common.exception.FlightAlreadyExistsException
import com.flightbooking.generated.jooq.tables.references.FLIGHTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

@Repository
class FlightDaoImpl(
    private val dslContext: DSLContext,
    private val mapper: FlightDaoMappers
) : FlightDao {

    private val logger = LoggerFactory.getLogger(FlightDaoImpl::class.java)
    private val flightStatusConverter = FlightStatusConverter()

    override suspend fun save(flightCreationRequest: FlightCreationRequest): Flight = withContext(Dispatchers.IO) {
        try {
            val flightId = UUID.randomUUID()
            logger.debug("Saving flight with ID: $flightId")

            val result = dslContext.insertInto(FLIGHTS)
                .set(FLIGHTS.FLIGHT_ID, flightId)
                .set(FLIGHTS.SOURCE_AIRPORT, flightCreationRequest.sourceAirport)
                .set(FLIGHTS.DESTINATION_AIRPORT, flightCreationRequest.destinationAirport)
                .set(FLIGHTS.DEPARTURE_TIME, toLocalDateTime(flightCreationRequest.departureTime))
                .set(FLIGHTS.ARRIVAL_TIME, toLocalDateTime(flightCreationRequest.arrivalTime))
                .set(FLIGHTS.AIRPLANE_ID, flightCreationRequest.airplaneId)
                .set(FLIGHTS.PRICE, flightCreationRequest.price)
                .set(FLIGHTS.STATUS, flightStatusConverter.to(FlightStatus.ACTIVE))
                .returningResult(FLIGHTS.asterisk())
                .fetchOne()

            requireNotNull(result) { "Failed to insert flight - no result returned" }

            val savedFlight = mapper.fromJooqRecord(result.into(FLIGHTS))
            logger.info("Successfully saved flight with ID: $flightId")
            savedFlight

        } catch (e: DataAccessException) {
            logger.error("Database access error while saving flight: ${e.message}", e)
            if (e.message?.contains("duplicate key") == true || e.message?.contains("unique constraint") == true) {
                throw FlightAlreadyExistsException(
                    "Flight at ${flightCreationRequest.departureTime} already exists",
                    e
                )
            }
            throw DatabaseException("Failed to save flight: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while saving flight: ${e.message}", e)
            throw DatabaseException("Database error occurred while saving flight: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while saving flight: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while saving flight: ${e.message}", e)
        }
    }

    override suspend fun findById(flightId: UUID): Flight? = withContext(Dispatchers.IO) {
        try {
            logger.debug("Finding flight by ID: $flightId")

            val result = dslContext.selectFrom(FLIGHTS)
                .where(FLIGHTS.FLIGHT_ID.eq(flightId))
                .fetchOne()

            result?.let { mapper.fromJooqRecord(it) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding flight by ID $flightId: ${e.message}", e)
            throw DatabaseException("Failed to find flight: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding flight by ID $flightId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding flight: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding flight by ID $flightId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding flight: ${e.message}", e)
        }
    }

    private fun toLocalDateTime(zonedDateTime: ZonedDateTime): LocalDateTime {
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    }

    private fun toZonedDateTime(localDateTime: LocalDateTime): ZonedDateTime {
        return localDateTime.atZone(ZoneOffset.UTC)
    }
}