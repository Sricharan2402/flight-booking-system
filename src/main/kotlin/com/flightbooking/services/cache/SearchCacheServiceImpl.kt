package com.flightbooking.services.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.flightbooking.domain.search.SearchResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate

@Service
class SearchCacheServiceImpl(
    @Qualifier("searchCacheRedisTemplate")
    private val searchCacheRedisTemplate: RedisTemplate<String, String>,
    @Value("\${redis.search.cache-ttl}")
    private val cacheTtl: Long
) : SearchCacheService {

    private val logger = LoggerFactory.getLogger(SearchCacheServiceImpl::class.java)
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

    override fun getCachedSearchResults(cacheKey: String): SearchResponse? {
        return try {
            val cachedJson = searchCacheRedisTemplate.opsForValue().get(cacheKey)
            cachedJson?.let {
                objectMapper.readValue(it, SearchResponse::class.java)
            }
        } catch (e: Exception) {
            logger.warn("Failed to retrieve cached search results for key: $cacheKey", e)
            null
        }
    }

    override fun cacheSearchResults(cacheKey: String, searchResponse: SearchResponse, ttlSeconds: Long) {
        try {
            val jsonValue = objectMapper.writeValueAsString(searchResponse)
            searchCacheRedisTemplate.opsForValue().set(cacheKey, jsonValue)
            searchCacheRedisTemplate.expire(cacheKey, Duration.ofSeconds(ttlSeconds))
            logger.debug("Cached search results for key={} ttl={}s", cacheKey, ttlSeconds)
        } catch (e: Exception) {
            logger.error("Failed to cache search results for key: $cacheKey", e)
        }
    }

    override fun invalidateCache(pattern: String) {
        try {
            val keys = searchCacheRedisTemplate.keys(pattern)
            if (keys.isNotEmpty()) {
                searchCacheRedisTemplate.delete(keys)
                logger.info("Invalidated ${keys.size} cache entries for pattern: $pattern")
            }
        } catch (e: Exception) {
            logger.error("Failed to invalidate cache for pattern: $pattern", e)
        }
    }

    override fun generateCacheKey(source: String, destination: String, date: LocalDate): String {
        return "journeys:$source:$destination:$date"
    }
}
