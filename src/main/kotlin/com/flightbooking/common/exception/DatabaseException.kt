package com.flightbooking.common.exception

class DatabaseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)