package com.lifeos.strategy.common.error;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import java.util.List;
import java.util.Map;

/**
 * Validation failures found in service code (rules that span several fields or need stored state), in the same
 * shape as Bean Validation failures: {@code VALIDATION_FAILED} with {@code errors: [{field, message}]}.
 */
public final class FieldErrors {

    private FieldErrors() {
    }

    public static LifeOsException invalid(String field, String message) {
        return new LifeOsException(CommonErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }
}
