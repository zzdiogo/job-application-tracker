package dev.zzdiogo.jobtracker.interview;

import dev.zzdiogo.jobtracker.application.JobApplicationService;
import dev.zzdiogo.jobtracker.common.Errors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Interview stages for one application. Every route first checks the caller owns it. */
@RestController
@RequestMapping("/api/applications/{applicationId}/interviews")
public class InterviewController {

    public record InterviewRequest(
            @NotNull InterviewStage stage,
            InterviewOutcome outcome,
            Instant scheduledAt,
            @Size(max = 2000) String notes) {}

    public record InterviewResponse(
            Long id, InterviewStage stage, InterviewOutcome outcome, Instant scheduledAt, String notes) {

        static InterviewResponse from(Interview interview) {
            return new InterviewResponse(
                    interview.getId(),
                    interview.getStage(),
                    interview.getOutcome(),
                    interview.getScheduledAt(),
                    interview.getNotes());
        }
    }

    private final InterviewRepository interviews;
    private final JobApplicationService applications;

    public InterviewController(InterviewRepository interviews, JobApplicationService applications) {
        this.interviews = interviews;
        this.applications = applications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<InterviewResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId) {
        requireOwned(jwt, applicationId);
        return interviews.findByApplicationIdOrderByScheduledAtAscIdAsc(applicationId).stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InterviewResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody InterviewRequest request) {
        requireOwned(jwt, applicationId);
        Interview interview = new Interview(applicationId);
        apply(interview, request);
        return InterviewResponse.from(interviews.save(interview));
    }

    @PutMapping("/{id}")
    @Transactional
    public InterviewResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @PathVariable Long id,
            @Valid @RequestBody InterviewRequest request) {
        requireOwned(jwt, applicationId);
        Interview interview = find(applicationId, id);
        apply(interview, request);
        return InterviewResponse.from(interview);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId, @PathVariable Long id) {
        requireOwned(jwt, applicationId);
        interviews.delete(find(applicationId, id));
    }

    private Interview find(Long applicationId, Long id) {
        return interviews.findByIdAndApplicationId(id, applicationId).orElseThrow(() -> Errors.notFound("Interview"));
    }

    private void requireOwned(Jwt jwt, Long applicationId) {
        applications.requireOwned(Long.valueOf(jwt.getSubject()), applicationId);
    }

    private static void apply(Interview interview, InterviewRequest request) {
        InterviewOutcome outcome = request.outcome() != null ? request.outcome() : InterviewOutcome.PENDING;
        interview.update(request.stage(), outcome, request.scheduledAt(), request.notes());
    }
}
