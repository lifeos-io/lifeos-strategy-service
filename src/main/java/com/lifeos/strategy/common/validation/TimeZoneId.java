package com.lifeos.strategy.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

/** An IANA time zone ID such as {@code Europe/Istanbul}; fixed offsets like {@code +03:00} are rejected. Null is valid. */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeZoneId.Validator.class)
public @interface TimeZoneId {

    String message() default "must be an IANA time zone, e.g. Europe/Istanbul";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TimeZoneId, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ZoneId.getAvailableZoneIds().contains(value);
        }
    }
}
