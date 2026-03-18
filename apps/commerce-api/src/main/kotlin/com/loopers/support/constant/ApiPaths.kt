package com.loopers.support.constant

object ApiPaths {

    object Users {
        const val BASE = "/api/v1/users"
        const val REGISTER = BASE
        const val ME = "$BASE/me"
        const val ME_PASSWORD = "$BASE/me/password"
    }

    object Examples {
        const val BASE = "/api/v1/examples"
        const val BY_ID = "$BASE/{id}"
    }

    object Payments {
        const val BASE = "/api/v1/payments"
        const val CALLBACK = "$BASE/callback"
        const val BY_ID = "$BASE/{paymentId}"
        const val RECOVER = "$BASE/{paymentId}/recover"
    }
}
