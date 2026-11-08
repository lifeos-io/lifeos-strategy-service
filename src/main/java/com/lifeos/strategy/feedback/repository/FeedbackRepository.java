package com.lifeos.strategy.feedback.repository;

import com.lifeos.strategy.feedback.entity.Feedback;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    Optional<Feedback> findByIdAndUserId(UUID id, UUID userId);

    /** Most recently received first. */
    List<Feedback> findByUserIdOrderByReceivedOnDescCreatedAtDesc(UUID userId);
}
