package com.lifeos.strategy.action.entity;

/** Any status can follow any other (a done action can be reopened, a dropped one picked up again). */
public enum ActionStatus {
    PLANNED,
    DONE,
    /** Pushed back; every time an action becomes DEFERRED its deferred count grows. */
    DEFERRED,
    /** No longer part of the week; its estimate no longer counts against the capacity. */
    DROPPED
}
