package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class UserAuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun authenticate(loginId: String, rawPassword: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorCode.AUTHENTICATION_FAILED)

        if (!passwordEncoder.matches(rawPassword, user.password.value)) {
            throw CoreException(UserErrorCode.AUTHENTICATION_FAILED)
        }

        return user
    }
}
