package com.lifeos.strategy.common.error;

import com.lifeos.base.error.ErrorCode;

/** Strategy's own error codes, next to the shared ones in {@code CommonErrorCode}. */
public enum StrategyErrorCode implements ErrorCode {

    /** The requested status change is not allowed, e.g. an achieved goal back to active. */
    STRATEGY_GOAL_STATUS_TRANSITION(409, "Goal status change not allowed"),

    /** An achieved or abandoned goal is read-only. */
    STRATEGY_GOAL_CLOSED(409, "Goal is closed"),

    /** A priority order must list every open goal exactly once. */
    STRATEGY_GOAL_ORDER_MISMATCH(400, "Goal order does not match the open goals"),

    /** The minutes given to goals exceed the week's capacity. */
    STRATEGY_CAPACITY_EXCEEDED(422, "Weekly capacity exceeded"),

    /** The week already has a plan. */
    STRATEGY_PLAN_EXISTS(409, "Weekly plan already exists"),

    /** Another week is active; close it before activating a new one. */
    STRATEGY_PLAN_ALREADY_ACTIVE(409, "Another weekly plan is active"),

    /** The plan's status does not allow the operation, e.g. editing a closed week. */
    STRATEGY_PLAN_STATUS(409, "Weekly plan status does not allow this"),

    /** The decision's status does not allow the operation, e.g. editing a decision that was already made. */
    STRATEGY_DECISION_STATUS(409, "Decision status does not allow this"),

    /** The decision was reopened already; the decision reopened from it is the one to work on. */
    STRATEGY_DECISION_ALREADY_REOPENED(409, "Decision already reopened"),

    /** The idea's status does not allow the operation, e.g. activating a dropped idea. */
    STRATEGY_IDEA_STATUS(409, "Idea status does not allow this"),

    /** Only an idea whose latest evaluation says PURSUE can become active. */
    STRATEGY_IDEA_NOT_PURSUED(409, "Idea not evaluated as worth pursuing"),

    /** Two ideas are already active; shelve, drop or promote one first. */
    STRATEGY_ACTIVE_IDEA_LIMIT(409, "Active idea limit reached"),

    /** The proposal was already decided (accepted, rejected or superseded). */
    STRATEGY_PROPOSAL_STATUS(409, "Proposal already decided"),

    /** The strategy changed since Coach made the proposal, so it can no longer be applied as it is. */
    STRATEGY_PROPOSAL_OUTDATED(409, "Proposal is outdated"),

    /** The user deleted their account (KVKK); a token issued before that cannot bring it back. */
    STRATEGY_USER_DELETED(410, "Account deleted");

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
