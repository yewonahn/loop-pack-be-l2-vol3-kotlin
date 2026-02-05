package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class UserFacade(
    private val userService: UserService,
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

    fun getMyInfo(user: User): UserInfo {
        return toUserInfo(user)
    }

    fun changePassword(user: User, currentPassword: String, newPassword: String) {
        userService.changePassword(user, currentPassword, newPassword)
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
