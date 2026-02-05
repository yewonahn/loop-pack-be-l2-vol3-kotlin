package com.loopers.interfaces.api

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.loopers.support.error.CommonErrorCode
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiControllerAdvice {
    private val log = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler
    fun handle(e: CoreException): ResponseEntity<ApiResponse<*>> {
        log.warn("CoreException: [{}] {}", e.errorCode.code, e.message, e)
        return failureResponse(errorCode = e.errorCode, errorMessage = e.message)
    }

    @ExceptionHandler
    fun handleBadRequest(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<*>> {
        val fieldErrors = e.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "유효하지 않은 값입니다.")
        }
        log.warn("Validation failed: {}", fieldErrors)
        return failureResponseWithFieldErrors(
            errorCode = CommonErrorCode.INVALID_INPUT_VALUE,
            fieldErrors = fieldErrors,
        )
    }

    /**
     * 요청 파라미터 타입 불일치
     */
    @ExceptionHandler
    fun handleBadRequest(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<*>> {
        val name = e.name
        val type = e.requiredType?.simpleName ?: "unknown"
        val value = e.value ?: "null"
        val message = "요청 파라미터 '$name' (타입: $type)의 값 '$value'이(가) 잘못되었습니다."
        return failureResponse(errorCode = CommonErrorCode.INVALID_TYPE_VALUE, errorMessage = message)
    }

    /**
     * 필수 요청 파라미터 누락
     */
    @ExceptionHandler
    fun handleBadRequest(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<*>> {
        val name = e.parameterName
        val type = e.parameterType
        val message = "필수 요청 파라미터 '$name' (타입: $type)가 누락되었습니다."
        return failureResponse(errorCode = CommonErrorCode.MISSING_REQUIRED_VALUE, errorMessage = message)
    }

    /**
     * HTTP 메시지 파싱 실패 (JSON 파싱 등)
     */
    @ExceptionHandler
    fun handleBadRequest(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<*>> {
        val errorMessage = when (val rootCause = e.rootCause) {
            is InvalidFormatException -> {
                val fieldName = rootCause.path.joinToString(".") { it.fieldName ?: "?" }

                val valueIndicationMessage = when {
                    rootCause.targetType.isEnum -> {
                        val enumClass = rootCause.targetType
                        val enumValues = enumClass.enumConstants.joinToString(", ") { it.toString() }
                        "사용 가능한 값 : [$enumValues]"
                    }

                    else -> ""
                }

                val expectedType = rootCause.targetType.simpleName
                val value = rootCause.value

                "필드 '$fieldName'의 값 '$value'이(가) 예상 타입($expectedType)과 일치하지 않습니다. $valueIndicationMessage"
            }

            is MismatchedInputException -> {
                val fieldPath = rootCause.path.joinToString(".") { it.fieldName ?: "?" }
                "필수 필드 '$fieldPath'이(가) 누락되었습니다."
            }

            is JsonMappingException -> {
                val fieldPath = rootCause.path.joinToString(".") { it.fieldName ?: "?" }
                "필드 '$fieldPath'에서 JSON 매핑 오류가 발생했습니다: ${rootCause.originalMessage}"
            }

            else -> "요청 본문을 처리하는 중 오류가 발생했습니다. JSON 메세지 규격을 확인해주세요."
        }

        return failureResponse(errorCode = CommonErrorCode.INVALID_INPUT_VALUE, errorMessage = errorMessage)
    }

    /**
     * 웹 입력 검증 실패
     */
    @ExceptionHandler
    fun handleBadRequest(e: ServerWebInputException): ResponseEntity<ApiResponse<*>> {
        fun extractMissingParameter(message: String): String {
            val regex = "'(.+?)'".toRegex()
            return regex.find(message)?.groupValues?.get(1) ?: ""
        }

        val missingParams = extractMissingParameter(e.reason ?: "")
        return if (missingParams.isNotEmpty()) {
            failureResponse(
                errorCode = CommonErrorCode.MISSING_REQUIRED_VALUE,
                errorMessage = "필수 요청 값 '$missingParams'가 누락되었습니다.",
            )
        } else {
            failureResponse(errorCode = CommonErrorCode.INVALID_INPUT_VALUE)
        }
    }

    /**
     * 리소스를 찾을 수 없음
     */
    @ExceptionHandler
    fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<*>> {
        return failureResponse(errorCode = CommonErrorCode.RESOURCE_NOT_FOUND)
    }

    /**
     * 그 외 모든 예외 (서버 에러)
     */
    @ExceptionHandler
    fun handle(e: Throwable): ResponseEntity<ApiResponse<*>> {
        log.error("Unhandled Exception: {}", e.message, e)
        return failureResponse(errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun failureResponse(errorCode: ErrorCode, errorMessage: String? = null): ResponseEntity<ApiResponse<*>> =
        ResponseEntity(
            ApiResponse.fail(errorCode = errorCode.code, errorMessage = errorMessage ?: errorCode.message),
            errorCode.status,
        )

    private fun failureResponseWithFieldErrors(
        errorCode: ErrorCode,
        fieldErrors: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity(
            ApiResponse.failWithFieldErrors(
                errorCode = errorCode.code,
                errorMessage = errorCode.message,
                fieldErrors = fieldErrors,
            ),
            errorCode.status,
        )
}
