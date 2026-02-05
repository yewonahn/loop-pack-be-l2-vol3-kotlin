package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.UserException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class UserFacade(
    private val userService: UserService,
    private val userRepository: UserRepository,
) {
    fun register(
        loginId: String,
        rawPassword: String,
        name: String,
        birthDate: LocalDate,
        email: String,
    ): UserInfo {
        val user = userService.register(
            loginId = loginId,
            rawPassword = rawPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
        return toUserInfo(user)
    }

    @Transactional(readOnly = true)
    fun getMyInfo(userId: Long): UserInfo {
        val user = userRepository.findById(userId)
            ?: throw UserException.invalidCredentials()
        return toUserInfo(user)
    }

    fun changePassword(userId: Long, currentPassword: String, newPassword: String) {
        userService.changePassword(userId, currentPassword, newPassword)
    }

    private fun toUserInfo(user: User): UserInfo {
        return UserInfo(
            loginId = user.loginId,
            name = maskLastCharacter(user.name),
            birthDate = user.birthDate,
            email = user.email,
        )
    }

    private fun maskLastCharacter(name: String): String {
        if (name.length <= 1) return "*"
        return name.dropLast(1) + "*"
    }
}
