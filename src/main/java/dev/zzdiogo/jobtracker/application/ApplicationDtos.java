package dev.zzdiogo.jobtracker.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

/** Request and response bodies for the application endpoints. */
public final class ApplicationDtos {

    private ApplicationDtos() {}

    /** Used to create and update. A null status means "keep it" (or WISHLIST when creating). */
    public record ApplicationRequest(
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 200) String role,
            // Only web links: the UI renders this as a link, so "javascript:" must never get in.
            @Size(max = 2048) @Pattern(regexp = "(?i)^https?://\\S+$", message = "must be an http(s) link") String url,
            @Size(max = 200) String location,
            ApplicationStatus status,
            LocalDate appliedOn,
            @Size(max = 5000) String notes) {}

    public record ApplicationResponse(
            Long id,
            String company,
            String role,
            String url,
            String location,
            ApplicationStatus status,
            LocalDate appliedOn,
            String notes,
            Instant createdAt,
            Instant updatedAt) {

        static ApplicationResponse from(JobApplication application) {
            return new ApplicationResponse(
                    application.getId(),
                    application.getCompany(),
                    application.getRole(),
                    application.getUrl(),
                    application.getLocation(),
                    application.getStatus(),
                    application.getAppliedOn(),
                    application.getNotes(),
                    application.getCreatedAt(),
                    application.getUpdatedAt());
        }
    }

    public record StatusChangeResponse(ApplicationStatus from, ApplicationStatus to, Instant changedAt) {

        static StatusChangeResponse from(StatusChange change) {
            return new StatusChangeResponse(change.getFromStatus(), change.getToStatus(), change.getChangedAt());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
