package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun register(
        loginId: String,
        rawPassword: String,
        name: String,
        birthDate: LocalDate,
        email: String,
    ): User {
        if (userRepository.existsByLoginId(loginId)) {
            throw CoreException(UserErrorCode.DUPLICATE_LOGIN_ID)
        }

        Password.validate(rawPassword, birthDate)

        val encodedPassword = passwordEncoder.encode(rawPassword)
        val user = User.create(
            loginId = loginId,
            encodedPassword = encodedPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
        return userRepository.save(user)
    }
}
