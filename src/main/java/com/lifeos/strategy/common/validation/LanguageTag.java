package com.lifeos.strategy.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;

/** A well-formed BCP 47 language tag with a language, such as {@code tr-TR} or {@code en}. Null is valid. */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LanguageTag.Validator.class)
public @interface LanguageTag {

    String message() default "must be a language tag, e.g. tr-TR";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<LanguageTag, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            Locale locale = Locale.forLanguageTag(value);
            return !locale.getLanguage().isEmpty() && locale.toLanguageTag().equalsIgnoreCase(value);
        }
    }
}
