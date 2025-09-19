package com.flightbooking.common.exception

import java.util.*

class InvalidAirplaneException(
    airplaneId: UUID,
    cause: Throwable? = null
) : RuntimeException("Airplane with ID $airplaneId not found or invalid", cause)