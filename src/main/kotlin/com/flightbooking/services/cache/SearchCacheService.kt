package com.flightbooking.services.cache

import com.flightbooking.domain.search.SearchResponse
import java.time.LocalDate

interface SearchCacheService {
    fun getCachedSearchResults(cacheKey: String): SearchResponse?
    fun cacheSearchResults(cacheKey: String, searchResponse: SearchResponse, ttlSeconds: Long)
    fun invalidateCache(pattern: String)
    fun generateCacheKey(source: String, destination: String, date: LocalDate): String
}