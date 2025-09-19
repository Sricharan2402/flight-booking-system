package com.flightbooking.data

import com.flightbooking.common.exception.DatabaseException
import com.flightbooking.generated.jooq.tables.references.AIRPLANES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.SQLException
import java.util.*

@Repository
class AirplaneDaoImpl(
    private val dslContext: DSLContext
) : AirplaneDao {

    private val logger = LoggerFactory.getLogger(AirplaneDaoImpl::class.java)

    override suspend fun existsById(airplaneId: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.debug("Checking if airplane exists with ID: $airplaneId")

            dslContext.fetchExists(
                AIRPLANES,
                AIRPLANES.AIRPLANE_ID.eq(airplaneId)
            )

        } catch (e: DataAccessException) {
            logger.error("Database access error while checking airplane existence for ID $airplaneId: ${e.message}", e)
            throw DatabaseException("Failed to check airplane existence: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while checking airplane existence for ID $airplaneId: ${e.message}", e)
            throw DatabaseException("Database error occurred while checking airplane existence: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while checking airplane existence for ID $airplaneId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while checking airplane existence: ${e.message}", e)
        }
    }

    override suspend fun isActiveById(airplaneId: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.debug("Checking if airplane is active with ID: $airplaneId")

            dslContext.fetchExists(
                AIRPLANES,
                AIRPLANES.AIRPLANE_ID.eq(airplaneId)
                    .and(AIRPLANES.STATUS.eq("ACTIVE"))
            )

        } catch (e: DataAccessException) {
            logger.error("Database access error while checking airplane active status for ID $airplaneId: ${e.message}", e)
            throw DatabaseException("Failed to check airplane active status: ${e.message}", e)
        } catch (e: SQLException) {
            logger.error("SQL error while checking airplane active status for ID $airplaneId: ${e.message}", e)
            throw DatabaseException("Database error occurred while checking airplane active status: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error while checking airplane active status for ID $airplaneId: ${e.message}", e)
            throw DatabaseException("Unexpected error occurred while checking airplane active status: ${e.message}", e)
        }
    }
}