package com.lifeos.strategy.common.error;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validation failures found in service code (rules that span several fields or need stored state), in the same
 * shape as Bean Validation failures: {@code VALIDATION_FAILED} with {@code errors: [{field, message}]}.
 *
 * <pre>{@code
 * FieldErrors errors = new FieldErrors();
 * if (sum != 100) errors.add("effortAllocation", "must add up to 100");
 * errors.throwIfAny();
 * }</pre>
 */
public final class FieldErrors {

    private final List<Map<String, String>> errors = new ArrayList<>();

    public static LifeOsException invalid(String field, String message) {
        return new FieldErrors().add(field, message).toException();
    }

    public FieldErrors add(String field, String message) {
        errors.add(Map.of("field", field, "message", message));
        return this;
    }

    public void throwIfAny() {
        if (!errors.isEmpty()) {
            throw toException();
        }
    }

    private LifeOsException toException() {
        return new LifeOsException(CommonErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                Map.of("errors", List.copyOf(errors)));
    }
}
