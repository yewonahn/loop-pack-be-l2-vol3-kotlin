package com.loopers.interfaces.api.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DateFormatValidator::class])
annotation class ValidDateFormat(
    val message: String = "날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식으로 입력해주세요.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class DateFormatValidator : ConstraintValidator<ValidDateFormat, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrBlank()) return true // null/blank 체크는 @NotBlank에게 위임

        return try {
            LocalDate.parse(value)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
