package com.lifeos.strategy.idea.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Objects;

/**
 * One look at an idea: how well it fits the goals, whether it would produce evidence, how much the user wants it,
 * what it costs per week, and the verdict. Scores are 1–5.
 */
@Embeddable
public class IdeaEvaluation {

    private int goalFit;
    private int evidencePotential;
    private int energy;
    private int weeklyMinutes;

    @Enumerated(EnumType.STRING)
    private IdeaVerdict verdict;

    private String note;
    private Instant evaluatedAt;

    protected IdeaEvaluation() {
    }

    public IdeaEvaluation(int goalFit, int evidencePotential, int energy, int weeklyMinutes, IdeaVerdict verdict,
            String note, Instant evaluatedAt) {
        this.goalFit = goalFit;
        this.evidencePotential = evidencePotential;
        this.energy = energy;
        this.weeklyMinutes = weeklyMinutes;
        this.verdict = Objects.requireNonNull(verdict);
        this.note = note;
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt);
    }

    public int getGoalFit() {
        return goalFit;
    }

    public int getEvidencePotential() {
        return evidencePotential;
    }

    public int getEnergy() {
        return energy;
    }

    public int getWeeklyMinutes() {
        return weeklyMinutes;
    }

    public IdeaVerdict getVerdict() {
        return verdict;
    }

    public String getNote() {
        return note;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    // Value equality, so Hibernate does not rewrite the collection on every flush.
    @Override
    public boolean equals(Object other) {
        return other instanceof IdeaEvaluation that
                && goalFit == that.goalFit
                && evidencePotential == that.evidencePotential
                && energy == that.energy
                && weeklyMinutes == that.weeklyMinutes
                && verdict == that.verdict
                && Objects.equals(note, that.note)
                && evaluatedAt.equals(that.evaluatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalFit, evidencePotential, energy, weeklyMinutes, verdict, note, evaluatedAt);
    }
}
