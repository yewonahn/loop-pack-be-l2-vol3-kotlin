package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun register(
        @Valid @RequestBody request: UserV1Dto.RegisterRequest,
    ): ApiResponse<UserV1Dto.UserResponse> {
        val userInfo = userFacade.register(
            loginId = request.loginId,
            rawPassword = request.password,
            name = request.name,
            birthDate = request.toBirthDate(),
            email = request.email,
        )
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo))
    }

    @GetMapping("/me")
    override fun getMe(@CurrentUser user: User): ApiResponse<UserV1Dto.UserResponse> {
        val userInfo = userFacade.getMyInfo(user)
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo))
    }

    @PatchMapping("/me/password")
    override fun changePassword(
        @CurrentUser user: User,
        @Valid @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userFacade.changePassword(user, request.currentPassword, request.newPassword)
        return ApiResponse.success(Unit)
    }
}
