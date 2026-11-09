package com.lifeos.strategy.support;

import com.lifeos.strategy.account.client.IdentityAccounts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for Keycloak in integration tests: records deletions instead of calling the admin API (no Keycloak
 * runs in the tests), and can be told to fail for a user to exercise the retry and dead-letter path.
 */
public class RecordingIdentityAccounts implements IdentityAccounts {

    private final List<UUID> calls = new CopyOnWriteArrayList<>();
    private final Set<UUID> deleted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();

    @Override
    public IdentityDeletion delete(UUID userId) {
        calls.add(userId);
        AtomicInteger failures = failuresLeft.get(userId);
        if (failures != null && failures.getAndDecrement() > 0) {
            throw new IllegalStateException("Keycloak is down (test)");
        }
        return deleted.add(userId) ? IdentityDeletion.DELETED : IdentityDeletion.ALREADY_GONE;
    }

    /** The next {@code times} deletions of {@code userId} fail. */
    public void failFor(UUID userId, int times) {
        failuresLeft.put(userId, new AtomicInteger(times));
    }

    /** How often a deletion of {@code userId} was attempted. */
    public long attempts(UUID userId) {
        return calls.stream().filter(userId::equals).count();
    }
}
