package dev.zzdiogo.jobtracker.application;

import dev.zzdiogo.jobtracker.application.ApplicationDtos.ApplicationRequest;
import dev.zzdiogo.jobtracker.application.ApplicationDtos.ApplicationResponse;
import dev.zzdiogo.jobtracker.application.ApplicationDtos.PageResponse;
import dev.zzdiogo.jobtracker.application.ApplicationDtos.StatusChangeResponse;
import dev.zzdiogo.jobtracker.common.Errors;
import dev.zzdiogo.jobtracker.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationService {

    private final JobApplicationRepository applications;
    private final StatusChangeRepository statusChanges;
    private final UserRepository users;
    private final Clock clock;

    public JobApplicationService(
            JobApplicationRepository applications,
            StatusChangeRepository statusChanges,
            UserRepository users,
            Clock clock) {
        this.applications = applications;
        this.statusChanges = statusChanges;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public ApplicationResponse create(Long userId, ApplicationRequest request) {
        ApplicationStatus status = request.status() != null ? request.status() : ApplicationStatus.WISHLIST;
        JobApplication application = new JobApplication(users.getReferenceById(userId), status, clock.instant());
        applyDetails(application, request);
        fillAppliedDate(application);
        applications.save(application);
        statusChanges.save(new StatusChange(application.getId(), null, status, clock.instant()));
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> list(Long userId, ApplicationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Page<JobApplication> results = status == null
                ? applications.findByUserId(userId, pageable)
                : applications.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(results.map(ApplicationResponse::from));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long userId, Long id) {
        return ApplicationResponse.from(requireOwned(userId, id));
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long id, ApplicationRequest request) {
        JobApplication application = requireOwned(userId, id);
        applyDetails(application, request);
        Instant now = clock.instant();
        ApplicationStatus previous = application.getStatus();
        if (request.status() != null && request.status() != previous) {
            application.setStatus(request.status());
            statusChanges.save(new StatusChange(id, previous, request.status(), now));
        }
        fillAppliedDate(application);
        application.setUpdatedAt(now);
        return ApplicationResponse.from(application);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        applications.delete(requireOwned(userId, id));
    }

    @Transactional(readOnly = true)
    public List<StatusChangeResponse> history(Long userId, Long id) {
        requireOwned(userId, id);
        return statusChanges.findByApplicationIdOrderByChangedAtAscIdAsc(id).stream()
                .map(StatusChangeResponse::from)
                .toList();
    }

    /** Loads an application if this user owns it. Other services use this to check ownership. */
    @Transactional(readOnly = true)
    public JobApplication requireOwned(Long userId, Long id) {
        return applications.findByIdAndUserId(id, userId).orElseThrow(() -> Errors.notFound("Application"));
    }

    private static void applyDetails(JobApplication application, ApplicationRequest request) {
        application.updateDetails(
                request.company().trim(),
                request.role().trim(),
                request.url(),
                request.location(),
                request.appliedOn(),
                request.notes());
    }

    /** Applying is the moment the date is known, so fill it in if the user left it blank. */
    private void fillAppliedDate(JobApplication application) {
        if (application.getAppliedOn() == null && application.getStatus() != ApplicationStatus.WISHLIST) {
            application.setAppliedOn(LocalDate.now(clock));
        }
    }
}
