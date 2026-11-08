package com.lifeos.strategy.weeklyreview.repository;

import com.lifeos.strategy.weeklyreview.entity.WeeklyReview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, UUID> {

    @EntityGraph(attributePaths = "goals")
    Optional<WeeklyReview> findByUserIdAndIsoWeek(UUID userId, String isoWeek);

    /** Newest week first. */
    @EntityGraph(attributePaths = "goals")
    List<WeeklyReview> findByUserIdOrderByIsoWeekDesc(UUID userId);
}
