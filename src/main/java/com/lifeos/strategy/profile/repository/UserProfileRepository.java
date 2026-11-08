package com.lifeos.strategy.profile.repository;

import com.lifeos.strategy.profile.entity.UserProfile;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * Creates the profile with the given defaults unless the user already has one. Safe when two first
     * requests of the same user race (PostgreSQL {@code ON CONFLICT DO NOTHING}).
     *
     * @return 1 when a row was created, 0 when it already existed
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO user_profile (user_id, timezone, locale, default_weekly_minutes, version, created_at, updated_at)
            VALUES (:userId, :timezone, :locale, :weeklyMinutes, 0, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("timezone") String timezone,
            @Param("locale") String locale, @Param("weeklyMinutes") int weeklyMinutes, @Param("now") Instant now);
}
