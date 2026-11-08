package com.lifeos.strategy.common.error;

import com.lifeos.base.error.ErrorCode;

/** Strategy's own error codes, next to the shared ones in {@code CommonErrorCode}. */
public enum StrategyErrorCode implements ErrorCode {

    /** The requested status change is not allowed, e.g. an achieved goal back to active. */
    STRATEGY_GOAL_STATUS_TRANSITION(409, "Goal status change not allowed"),

    /** An achieved or abandoned goal is read-only. */
    STRATEGY_GOAL_CLOSED(409, "Goal is closed"),

    /** A priority order must list every open goal exactly once. */
    STRATEGY_GOAL_ORDER_MISMATCH(400, "Goal order does not match the open goals");

    private final int status;
    private final String title;

    StrategyErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
