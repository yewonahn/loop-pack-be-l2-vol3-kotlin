package com.loopers.interfaces.api.auth

import com.loopers.domain.user.UserAuthService
import com.loopers.support.error.UserException
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserIdArgumentResolver(
    private val userAuthService: UserAuthService,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentUserId::class.java) &&
            parameter.parameterType == Long::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val loginId = webRequest.getHeader(HEADER_LOGIN_ID)
            ?: throw UserException.invalidCredentials()
        val password = webRequest.getHeader(HEADER_LOGIN_PW)
            ?: throw UserException.invalidCredentials()

        return userAuthService.authenticateAndGetId(loginId, password)
    }

    companion object {
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }
}
