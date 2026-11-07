package dev.zzdiogo.jobtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.zzdiogo.jobtracker.application.JobApplication;
import dev.zzdiogo.jobtracker.application.JobApplicationRepository;
import dev.zzdiogo.jobtracker.reminder.Reminder;
import dev.zzdiogo.jobtracker.reminder.ReminderRepository;
import dev.zzdiogo.jobtracker.reminder.ReminderScheduler;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// A long delay stops the background schedule, so each test decides when checks run.
@TestPropertySource(properties = "app.reminder-check-delay=PT24H")
class ReminderApiTest extends ApiTestSupport {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    ReminderScheduler scheduler;

    @Autowired
    ReminderRepository reminders;

    @Autowired
    JobApplicationRepository applications;

    @BeforeEach
    void clearDueReminders() {
        reminders.deleteAll();
        reset(mailSender);
    }

    /** Saves directly, because the API (rightly) refuses reminders set in the past. */
    private Long saveDueReminder(long applicationId, String message) {
        JobApplication application = applications.findById(applicationId).orElseThrow();
        return reminders.save(new Reminder(application, Instant.now().minus(1, ChronoUnit.MINUTES), message)).getId();
    }

    @Test
    void dueReminderIsEmailedToItsOwnerOnlyOnce() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        long applicationId = createApplication(token, """
                {"company": "Acme", "role": "Java Dev", "url": "https://acme.example/job"}""");
        Long id = saveDueReminder(applicationId, "Follow up with the recruiter");

        scheduler.sendDueReminders();
        scheduler.sendDueReminders();

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly(email);
        assertThat(mail.getValue().getSubject()).isEqualTo("Reminder: Java Dev at Acme");
        assertThat(mail.getValue().getText())
                .contains("Follow up with the recruiter")
                .contains("https://acme.example/job");
        assertThat(reminders.findById(id).orElseThrow().getSentAt()).isNotNull();
    }

    @Test
    void futureRemindersAreNotSent() throws Exception {
        String token = register();
        long applicationId = createApplication(token);
        mvc.perform(json(auth(post("/api/applications/" + applicationId + "/reminders"), token), """
                        {"remindAt": "2099-01-01T09:00:00Z", "message": "Later"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sentAt").doesNotExist());

        scheduler.sendDueReminders();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void failedSendIsRetriedThenGivenUp() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        String token = register();
        Long id = saveDueReminder(createApplication(token), "Follow up");

        for (int run = 0; run < 8; run++) {
            scheduler.sendDueReminders();
        }

        verify(mailSender, times(5)).send(any(SimpleMailMessage.class));
        Reminder reminder = reminders.findById(id).orElseThrow();
        assertThat(reminder.getSentAt()).isNull();
        assertThat(reminder.getAttempts()).isEqualTo(5);
    }

    @Test
    void oneFailureDoesNotBlockOtherReminders() throws Exception {
        String token = register();
        long applicationId = createApplication(token);
        Long first = saveDueReminder(applicationId, "first");
        Long second = saveDueReminder(applicationId, "second");
        doThrow(new MailSendException("bad address")).doAnswer(call -> null)
                .when(mailSender).send(any(SimpleMailMessage.class));

        scheduler.sendDueReminders();

        boolean firstSent = reminders.findById(first).orElseThrow().getSentAt() != null;
        boolean secondSent = reminders.findById(second).orElseThrow().getSentAt() != null;
        assertThat(firstSent ^ secondSent).isTrue();
    }

    @Test
    void lineBreaksCannotInjectMailHeaders() throws Exception {
        String token = register();
        long applicationId = createApplication(token, """
                {"company": "Acme\\r\\nBcc: victim@example.com", "role": "Dev"}""");
        saveDueReminder(applicationId, "Hello");

        scheduler.sendDueReminders();

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getSubject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void pastDatesAndEmptyMessagesAreRejected() throws Exception {
        String token = register();
        long applicationId = createApplication(token);
        String path = "/api/applications/" + applicationId + "/reminders";
        mvc.perform(json(auth(post(path), token), """
                {"remindAt": "2001-01-01T00:00:00Z", "message": "Too late"}""")).andExpect(status().isBadRequest());
        mvc.perform(json(auth(post(path), token), """
                {"remindAt": "2099-01-01T00:00:00Z", "message": " "}""")).andExpect(status().isBadRequest());
    }

    @Test
    void remindersCanBeListedAndDeleted() throws Exception {
        String token = register();
        long applicationId = createApplication(token);
        String path = "/api/applications/" + applicationId + "/reminders";
        Long id = saveDueReminder(applicationId, "Follow up");

        mvc.perform(auth(get(path), token)).andExpect(jsonPath("$[0].message").value("Follow up"));
        mvc.perform(auth(delete(path + "/" + id), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get(path), token)).andExpect(jsonPath("$").isEmpty());
    }
}
