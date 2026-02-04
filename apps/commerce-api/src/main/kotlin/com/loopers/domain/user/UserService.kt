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
        // 1. 로그인 ID 중복 체크
        if (userRepository.existsByLoginId(loginId)) {
            throw CoreException(UserErrorCode.DUPLICATE_LOGIN_ID)
        }

        // 2. 비밀번호 검증 (규칙 체크)
        Password.validate(rawPassword, birthDate)

        // 3. 비밀번호 암호화
        val encodedPassword = passwordEncoder.encode(rawPassword)

        // 4. User 생성 및 저장
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
