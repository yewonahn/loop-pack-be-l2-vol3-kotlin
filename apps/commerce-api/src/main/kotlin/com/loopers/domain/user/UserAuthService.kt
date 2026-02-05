package com.loopers.domain.user

import com.loopers.support.error.UserException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class UserAuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun authenticate(loginId: String, rawPassword: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw UserException.invalidCredentials()

        if (!passwordEncoder.matches(rawPassword, user.password.value)) {
            throw UserException.invalidCredentials()
        }

        return user
    }

    fun authenticateAndGetId(loginId: String, rawPassword: String): Long {
        val user = authenticate(loginId, rawPassword)
        return requireNotNull(user.id) { "Authenticated user must have an id" }
    }
}
