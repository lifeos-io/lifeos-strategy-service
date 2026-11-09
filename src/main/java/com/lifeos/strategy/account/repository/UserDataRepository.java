package com.lifeos.strategy.account.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Deletes everything Strategy holds for one user, table by table in foreign-key order, with plain SQL: bulk
 * deletes, and the child tables without a {@code user_id} (phases, alternatives, evaluations...) go with their
 * parents through {@code ON DELETE CASCADE}. A new table with user data must be added here; the deletion test
 * fails for any {@code user_id} table that still has rows afterwards. {@code audit_log} is kept on purpose.
 */
@Repository
public class UserDataRepository {

    /** Tables in deletion order: every table comes before the tables it references. */
    private static final List<String> TABLES = List.of(
            "change_proposal",   // -> goal, strategy_version (its sources cascade)
            "weekly_review",     // -> weekly_plan, goal (its goals cascade)
            "idea",              // -> goal (its evaluations cascade)
            "decision",          // -> goal, knowledge_item (alternatives, assumptions, triggers cascade)
            "evidence",          // -> action, feedback, itself (all at once: checked at the end of the statement)
            "feedback",
            "action",            // -> weekly_plan, goal (its goals cascade)
            "weekly_plan",       // -> strategy_version, goal (its split cascades)
            "knowledge_item",    // -> goal
            "strategy_version",  // -> goal (phases and effort cascade)
            "goal",              // -> user_profile
            "user_profile",
            // lifeos-base's outbox: the user's past and unsent events (the deletion event is appended after this).
            "outbox_event");

    private final JdbcClient jdbcClient;

    public UserDataRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Deletes the user's rows; call it in a transaction, after locking the user's profile.
     *
     * @return rows deleted per table, in deletion order
     */
    public Map<String, Integer> deleteAll(UUID userId) {
        // change_proposal and strategy_version reference each other: break the cycle first.
        jdbcClient.sql("UPDATE strategy_version SET source_proposal_id = NULL WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        Map<String, Integer> deleted = new LinkedHashMap<>();
        for (String table : TABLES) {
            deleted.put(table, jdbcClient.sql("DELETE FROM " + table + " WHERE user_id = :userId")
                    .param("userId", userId)
                    .update());
        }
        return deleted;
    }
}
