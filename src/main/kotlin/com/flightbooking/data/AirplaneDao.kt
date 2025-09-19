package com.flightbooking.data

import java.util.*

interface AirplaneDao {

    suspend fun existsById(airplaneId: UUID): Boolean
    suspend fun isActiveById(airplaneId: UUID): Boolean
}