package com.loopers.domain.user

import com.loopers.support.error.UserException
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
            throw UserException.duplicateLoginId()
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

    @Transactional
    fun changePassword(userId: Long, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId)
            ?: throw UserException.invalidCredentials()

        if (!passwordEncoder.matches(currentPassword, user.password.value)) {
            throw UserException.invalidCurrentPassword()
        }

        if (passwordEncoder.matches(newPassword, user.password.value)) {
            throw UserException.samePassword()
        }

        Password.validate(newPassword, user.birthDate)

        val newEncodedPassword = passwordEncoder.encode(newPassword)
        user.changePassword(newEncodedPassword)
    }
}
