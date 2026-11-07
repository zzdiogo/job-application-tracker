package dev.zzdiogo.jobtracker.reminder;

import dev.zzdiogo.jobtracker.application.JobApplication;
import dev.zzdiogo.jobtracker.application.JobApplicationService;
import dev.zzdiogo.jobtracker.common.Errors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reminders for one application. Every route first checks the caller owns it. */
@RestController
@RequestMapping("/api/applications/{applicationId}/reminders")
public class ReminderController {

    public record ReminderRequest(
            @NotNull @Future Instant remindAt,
            @NotBlank @Size(max = 500) String message) {}

    public record ReminderResponse(Long id, Instant remindAt, String message, Instant sentAt) {

        static ReminderResponse from(Reminder reminder) {
            return new ReminderResponse(
                    reminder.getId(), reminder.getRemindAt(), reminder.getMessage(), reminder.getSentAt());
        }
    }

    private final ReminderRepository reminders;
    private final JobApplicationService applications;

    public ReminderController(ReminderRepository reminders, JobApplicationService applications) {
        this.reminders = reminders;
        this.applications = applications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ReminderResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId) {
        requireOwned(jwt, applicationId);
        return reminders.findByApplication_IdOrderByRemindAtAscIdAsc(applicationId).stream()
                .map(ReminderResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReminderResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody ReminderRequest request) {
        JobApplication application = requireOwned(jwt, applicationId);
        Reminder reminder = new Reminder(application, request.remindAt(), request.message().trim());
        return ReminderResponse.from(reminders.save(reminder));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId, @PathVariable Long id) {
        requireOwned(jwt, applicationId);
        Reminder reminder = reminders.findByIdAndApplication_Id(id, applicationId)
                .orElseThrow(() -> Errors.notFound("Reminder"));
        reminders.delete(reminder);
    }

    private JobApplication requireOwned(Jwt jwt, Long applicationId) {
        return applications.requireOwned(Long.valueOf(jwt.getSubject()), applicationId);
    }
}
