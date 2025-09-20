package com.flightbooking.data

import com.flightbooking.common.exception.DatabaseException
import com.flightbooking.data.mapper.FlightDaoMappers
import com.flightbooking.data.mapper.JourneyDaoMappers
import com.flightbooking.data.mapper.JourneyStatusConverter
import com.flightbooking.data.mapper.FlightStatusConverter
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.domain.common.JourneyStatus
import com.flightbooking.domain.common.FlightStatus
import com.flightbooking.generated.jooq.tables.references.FLIGHTS
import com.flightbooking.generated.jooq.tables.references.JOURNEYS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

@Repository
class JourneyDaoImpl(
    private val dslContext: DSLContext,
    private val journeyMapper: JourneyDaoMappers,
    private val flightMapper: FlightDaoMappers
) : JourneyDao {

    private val logger = LoggerFactory.getLogger(JourneyDaoImpl::class.java)
    private val journeyStatusConverter = JourneyStatusConverter()
    private val flightStatusConverter = FlightStatusConverter()

    override fun save(journey: Journey): Journey {
        return try {
            val journeyId = UUID.randomUUID()
            logger.debug("Saving journey with ID: $journeyId")

            val flightDetailsJson = journeyMapper.toFlightDetailsJson(journey.flightDetails)

            val result = dslContext.insertInto(JOURNEYS)
                .set(JOURNEYS.JOURNEY_ID, journeyId)
                .set(JOURNEYS.FLIGHT_DETAILS, JSONB.valueOf(flightDetailsJson))
                .set(JOURNEYS.SOURCE_AIRPORT, journey.sourceAirport)
                .set(JOURNEYS.DESTINATION_AIRPORT, journey.destinationAirport)
                .set(JOURNEYS.DEPARTURE_TIME, toLocalDateTime(journey.departureTime))
                .set(JOURNEYS.ARRIVAL_TIME, toLocalDateTime(journey.arrivalTime))
                .set(JOURNEYS.TOTAL_PRICE, journey.totalPrice)
                .set(JOURNEYS.STATUS, journeyStatusConverter.to(journey.status))
                .returningResult(JOURNEYS.asterisk())
                .fetchOne()

            requireNotNull(result) { "Failed to insert journey - no result returned" }

            val savedJourney = journeyMapper.fromJooqRecord(result.into(JOURNEYS))
            logger.info("Successfully saved journey with ID: $journeyId")
            savedJourney

        } catch (e: DataAccessException) {
            logger.error("Database access error while saving journey: ${e.message}", e)
            throw DatabaseException("Failed to save journey: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while saving journey: ${e.message}", e)
            throw DatabaseException("Database error occurred while saving journey: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while saving journey: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while saving journey: ${e.message}", e)
        }
    }

    override fun findBySourceAndDestinationAndDate(
        source: String,
        destination: String,
        date: LocalDate
    ): List<Journey> {
        return try {
            logger.debug("Finding journeys from $source to $destination on $date")

            val results = dslContext.selectFrom(JOURNEYS)
                .where(JOURNEYS.SOURCE_AIRPORT.eq(source))
                .and(JOURNEYS.DESTINATION_AIRPORT.eq(destination))
                .and(JOURNEYS.DEPARTURE_TIME.cast(java.sql.Date::class.java).eq(java.sql.Date.valueOf(date)))
                .and(JOURNEYS.STATUS.eq(journeyStatusConverter.to(JourneyStatus.ACTIVE)))
                .fetch()

            results.map { record -> journeyMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding journeys: ${e.message}", e)
            throw DatabaseException("Failed to find journeys: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding journeys: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding journeys: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding journeys: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding journeys: ${e.message}", e)
        }
    }

    override fun findFlightsByDate(date: LocalDate): List<Flight> {
        return try {
            logger.debug("Finding flights on date: $date")

            val results = dslContext.selectFrom(FLIGHTS)
                .where(FLIGHTS.DEPARTURE_TIME.cast(java.sql.Date::class.java).eq(java.sql.Date.valueOf(date)))
                .and(FLIGHTS.STATUS.eq(flightStatusConverter.to(FlightStatus.ACTIVE)))
                .fetch()

            results.map { record -> flightMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding flights by date: ${e.message}", e)
            throw DatabaseException("Failed to find flights: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding flights by date: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding flights: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding flights by date: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding flights: ${e.message}", e)
        }
    }

    override fun findById(journeyId: UUID): Journey? {
        return try {
            logger.debug("Finding journey by ID: $journeyId")

            val result = dslContext.selectFrom(JOURNEYS)
                .where(JOURNEYS.JOURNEY_ID.eq(journeyId))
                .fetchOne()

            result?.let { journeyMapper.fromJooqRecord(it) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding journey by ID $journeyId: ${e.message}", e)
            throw DatabaseException("Failed to find journey: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding journey by ID $journeyId: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding journey: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding journey by ID $journeyId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding journey: ${e.message}", e)
        }
    }

    override fun count(): Long {
        return try {
            logger.debug("Counting total journeys")

            val result = dslContext.selectCount()
                .from(JOURNEYS)
                .fetchOne(0, Long::class.java)

            result ?: 0L

        } catch (e: DataAccessException) {
            logger.error("Database access error while counting journeys: ${e.message}", e)
            throw DatabaseException("Failed to count journeys: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while counting journeys: ${e.message}", e)
            throw DatabaseException("Database error occurred while counting journeys: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while counting journeys: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while counting journeys: ${e.message}", e)
        }
    }

    override fun findAll(): List<Journey> {
        return try {
            logger.debug("Finding all journeys")

            val results = dslContext.selectFrom(JOURNEYS)
                .fetch()

            results.map { record -> journeyMapper.fromJooqRecord(record) }

        } catch (e: DataAccessException) {
            logger.error("Database access error while finding all journeys: ${e.message}", e)
            throw DatabaseException("Failed to find all journeys: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while finding all journeys: ${e.message}", e)
            throw DatabaseException("Database error occurred while finding all journeys: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while finding all journeys: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while finding all journeys: ${e.message}", e)
        }
    }

    override fun deleteAll(): Int {
        return try {
            logger.debug("Deleting all journeys")

            val deletedCount = dslContext.deleteFrom(JOURNEYS)
                .execute()

            logger.info("Successfully deleted $deletedCount journeys")
            deletedCount

        } catch (e: DataAccessException) {
            logger.error("Database access error while deleting all journeys: ${e.message}", e)
            throw DatabaseException("Failed to delete all journeys: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while deleting all journeys: ${e.message}", e)
            throw DatabaseException("Database error occurred while deleting all journeys: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while deleting all journeys: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while deleting all journeys: ${e.message}", e)
        }
    }

    private fun toLocalDateTime(zonedDateTime: ZonedDateTime): LocalDateTime {
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    }
}