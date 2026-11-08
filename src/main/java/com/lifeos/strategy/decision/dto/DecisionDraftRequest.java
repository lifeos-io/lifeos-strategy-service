package com.lifeos.strategy.decision.dto;

import com.lifeos.strategy.decision.entity.TriggerKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The content of a draft decision: {@code POST /api/strategy/decisions} creates one, {@code PUT .../{id}}
 * replaces it (with {@code version}). Alternatives are labelled A, B, C... in order.
 *
 * @param version        for {@code PUT}: the version the client last read; ignored on {@code POST}
 * @param goalId         one of the user's open goals, if the decision belongs to one
 * @param assumptionIds  the user's knowledge items the decision rests on
 * @param reopenTriggers what should make the user reconsider
 */
public record DecisionDraftRequest(
        Integer version,
        UUID goalId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String context,
        @NotNull @Size(max = 6) List<@Valid @NotNull AlternativeRequest> alternatives,
        @Size(max = 20) Set<@NotNull UUID> assumptionIds,
        @Size(max = 5) List<@Valid @NotNull TriggerRequest> reopenTriggers) {

    /** @param rejectionReason why it is not (going to be) chosen; needed for every alternative but the chosen one */
    public record AlternativeRequest(
            @NotBlank @Size(max = 1000) String description,
            @Size(max = 1000) String rejectionReason) {
    }

    /**
     * @param eventType for EVENT triggers that a system event can detect, e.g. {@code KnowledgeItemChanged}
     * @param firesOn   required for DATE triggers, in the future
     */
    public record TriggerRequest(
            @NotNull TriggerKind kind,
            @NotBlank @Size(max = 500) String description,
            @Pattern(regexp = "[A-Z][A-Za-z0-9]*") String eventType,
            LocalDate firesOn) {
    }
}
