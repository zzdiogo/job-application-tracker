package dev.zzdiogo.jobtracker.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "interviews")
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewOutcome outcome;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    private String notes;

    protected Interview() {}

    public Interview(Long applicationId) {
        this.applicationId = applicationId;
    }

    public void update(InterviewStage stage, InterviewOutcome outcome, Instant scheduledAt, String notes) {
        this.stage = stage;
        this.outcome = outcome;
        this.scheduledAt = scheduledAt;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public InterviewStage getStage() {
        return stage;
    }

    public InterviewOutcome getOutcome() {
        return outcome;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getNotes() {
        return notes;
    }
}
