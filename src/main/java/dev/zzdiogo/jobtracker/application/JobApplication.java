package dev.zzdiogo.jobtracker.application;

import dev.zzdiogo.jobtracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    private String url;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "applied_on")
    private LocalDate appliedOn;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {}

    public JobApplication(User user, ApplicationStatus status, Instant now) {
        this.user = user;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String company, String role, String url, String location, LocalDate appliedOn, String notes) {
        this.company = company;
        this.role = role;
        this.url = url;
        this.location = location;
        this.appliedOn = appliedOn;
        this.notes = notes;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public void setAppliedOn(LocalDate appliedOn) {
        this.appliedOn = appliedOn;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCompany() {
        return company;
    }

    public String getRole() {
        return role;
    }

    public String getUrl() {
        return url;
    }

    public String getLocation() {
        return location;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
