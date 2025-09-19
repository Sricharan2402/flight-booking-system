package com.flightbooking.common.exception

class FlightAlreadyExistsException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)