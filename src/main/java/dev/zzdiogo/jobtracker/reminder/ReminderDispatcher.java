package dev.zzdiogo.jobtracker.reminder;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Delivers one reminder inside its own transaction, so one failure can't affect the others. */
@Component
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);

    private final ReminderRepository reminders;
    private final ReminderMailer mailer;
    private final Clock clock;

    public ReminderDispatcher(ReminderRepository reminders, ReminderMailer mailer, Clock clock) {
        this.reminders = reminders;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void deliver(Long reminderId) {
        reminders.findById(reminderId).ifPresent(reminder -> {
            try {
                mailer.send(reminder);
                reminder.markSent(clock.instant());
            } catch (MailException exception) {
                reminder.markFailed();
                log.warn("Could not send reminder {} (attempt {}): {}",
                        reminderId, reminder.getAttempts(), exception.getMessage());
            }
        });
    }
}
