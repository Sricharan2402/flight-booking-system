package com.flightbooking.services.reservation

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class SeatReservationServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val stringRedisTemplate: StringRedisTemplate,
    @Value("\${redis.booking.reservation-ttl}") private val reservationTtl: Long
) : SeatReservationService {

    init {
        println("initialized redis")
    }

    private val logger = LoggerFactory.getLogger(SeatReservationServiceImpl::class.java)

    override fun reserveSeats(flightId: UUID, seatIds: List<UUID>, reservationTtlSeconds: Long): Boolean {
        return try {
            val reservationKey = generateReservationKey(flightId)
            val currentTime = System.currentTimeMillis()
            val expiryTime = currentTime + (reservationTtlSeconds * 1000)

            // Use Lua script for atomic check-and-reserve operation
            val luaScript = """
                -- ARGV[1] = currentTime
                -- ARGV[2..N-1] = seatId, expiryTime (pairs)
                -- ARGV[N] = key TTL (with buffer)
                
                -- Step 1: cleanup expired reservations
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
                
                -- Step 2: check if any seat is already reserved
                for i = 2, #ARGV - 1, 2 do
                    local seatId = ARGV[i]
                    if redis.call('ZSCORE', KEYS[1], seatId) then
                        return 0 -- failure: seat already reserved
                    end
                end
                
                -- Step 3: reserve all seats with their expiry times
                for i = 2, #ARGV - 1, 2 do
                    local seatId = ARGV[i]
                    local expiryTime = ARGV[i + 1]
                    redis.call('ZADD', KEYS[1], expiryTime, seatId)
                end
                
                -- Step 4: set TTL on the key itself
                redis.call('EXPIRE', KEYS[1], ARGV[#ARGV])
                
                return 1 -- success
            """.trimIndent()

            // Prepare arguments: [currentTime, seatId1, expiryTime1, seatId2, expiryTime2, ..., keyTtl]
            val args = mutableListOf<String>().apply {
                add(currentTime.toString()) // ARGV[1] - current time for cleanup
                seatIds.forEach { seatId ->
                    add(seatId.toString())    // ARGV[i] - seat ID
                    add(expiryTime.toString()) // ARGV[i+1] - expiry time
                }
                add((reservationTtlSeconds + 300).toString()) // ARGV[#ARGV] - key TTL with buffer
            }

            // Use RedisScript approach
            val script = DefaultRedisScript<Long>().apply {
                setScriptText(luaScript)
                resultType = Long::class.java
            }
            val result = stringRedisTemplate.execute(script, listOf(reservationKey), *args.toTypedArray())

            if (result == 1L) {
                logger.info("Reserved ${seatIds.size} seats for flight $flightId until $expiryTime")
                true
            } else {
                logger.warn("Failed to reserve seats for flight $flightId - some seats already reserved")
                false
            }

        } catch (e: Exception) {
            logger.error("Failed to reserve seats for flight $flightId", e)
            false
        }
    }

    override fun releaseSeats(flightId: UUID, seatIds: List<UUID>): Boolean {
        return try {
            val reservationKey = generateReservationKey(flightId)
            val zSetOps = redisTemplate.opsForZSet()

            val removedCount = zSetOps.remove(reservationKey, *seatIds.map { it.toString() }.toTypedArray())

            logger.debug("Released $removedCount seats for flight $flightId")
            true

        } catch (e: Exception) {
            logger.error("Failed to release seats for flight $flightId", e)
            false
        }
    }

    override fun getAvailableSeats(flightId: UUID, allSeats: List<UUID>): List<UUID> {
        return try {
            val reservationKey = generateReservationKey(flightId)

            // Clean up expired reservations first
            cleanupExpiredReservations(flightId)

            val zSetOps = redisTemplate.opsForZSet()

            // Get all currently reserved seats
            val reservedSeatIds = zSetOps.range(reservationKey, 0, -1)
                ?.map { UUID.fromString(it.toString()) }
                ?.toSet() ?: emptySet()

            // Filter out reserved seats from all available seats
            val availableSeats = allSeats.filterNot { seatId ->
                reservedSeatIds.contains(seatId)
            }

            logger.debug("Flight $flightId: ${allSeats.size} total seats, ${reservedSeatIds.size} reserved, ${availableSeats.size} available")
            availableSeats

        } catch (e: Exception) {
            logger.error("Failed to get available seats for flight $flightId", e)
            allSeats // Return all seats if Redis fails (fallback to DB validation)
        }
    }

    override fun cleanupExpiredReservations(flightId: UUID): Int {
        return try {
            val reservationKey = generateReservationKey(flightId)
            val currentTime = System.currentTimeMillis()

            val zSetOps = redisTemplate.opsForZSet()

            // Remove all entries with score (expiry time) less than current time
            val removedCount = zSetOps.removeRangeByScore(reservationKey, 0.0, currentTime.toDouble())

            if (removedCount != null && removedCount > 0) {
                logger.debug("Cleaned up $removedCount expired reservations for flight $flightId")
            }

            removedCount?.toInt() ?: 0

        } catch (e: Exception) {
            logger.error("Failed to cleanup expired reservations for flight $flightId", e)
            0
        }
    }

    override fun generateReservationKey(flightId: UUID): String {
        return "seat_reservations:$flightId"
    }
}