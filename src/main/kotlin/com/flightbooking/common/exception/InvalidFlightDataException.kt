package com.flightbooking.common.exception

class InvalidFlightDataException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)