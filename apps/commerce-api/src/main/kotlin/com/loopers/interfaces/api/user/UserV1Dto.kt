package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.interfaces.api.validation.ValidDateFormat
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

class UserV1Dto {

    data class RegisterRequest(
        @field:NotBlank(message = "로그인 ID는 필수입니다.")
        @field:Size(min = 4, max = 20, message = "로그인 ID는 4자 이상 20자 이하여야 합니다.")
        val loginId: String,

        @field:NotBlank(message = "비밀번호는 필수입니다.")
        @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        val password: String,

        @field:NotBlank(message = "이름은 필수입니다.")
        @field:Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        val name: String,

        @field:NotBlank(message = "생년월일은 필수입니다.")
        @field:ValidDateFormat
        val birthDate: String,

        @field:NotBlank(message = "이메일은 필수입니다.")
        @field:Email(message = "이메일 형식이 올바르지 않습니다.")
        val email: String,
    ) {
        fun toBirthDate(): LocalDate = LocalDate.parse(birthDate)
    }

    data class UserResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): UserResponse {
                return UserResponse(
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate,
                    email = info.email,
                )
            }
        }
    }

    data class ChangePasswordRequest(
        @field:NotBlank(message = "현재 비밀번호는 필수입니다.")
        val currentPassword: String,

        @field:NotBlank(message = "새 비밀번호는 필수입니다.")
        @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        val newPassword: String,
    )
}
