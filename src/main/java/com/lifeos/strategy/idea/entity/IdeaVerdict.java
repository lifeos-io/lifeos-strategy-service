package com.lifeos.strategy.idea.entity;

/** What an evaluation concludes. Only an idea whose latest verdict is PURSUE may become ACTIVE. */
public enum IdeaVerdict {
    /** Worth one of the two active slots. */
    PURSUE,
    /** Good, but not now. */
    PARK,
    /** Not worth doing. */
    DROP
}
