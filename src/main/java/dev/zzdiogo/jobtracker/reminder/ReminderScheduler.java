package dev.zzdiogo.jobtracker.reminder;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Looks for due reminders. A fixed delay means a slow run can never overlap the next one. */
@Component
public class ReminderScheduler {

    /** Stops retrying eventually, so a bad address can't fail forever. */
    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderRepository reminders;
    private final ReminderDispatcher dispatcher;
    private final ReminderMailer mailer;
    private final Clock clock;

    public ReminderScheduler(
            ReminderRepository reminders, ReminderDispatcher dispatcher, ReminderMailer mailer, Clock clock) {
        this.reminders = reminders;
        this.dispatcher = dispatcher;
        this.mailer = mailer;
        this.clock = clock;
        if (!mailer.isConfigured()) {
            log.info("SPRING_MAIL_HOST isn't set, so reminders stay in the app and no email is sent");
        }
    }

    @Scheduled(fixedDelayString = "${app.reminder-check-delay:PT1M}")
    public void sendDueReminders() {
        if (!mailer.isConfigured()) {
            return;
        }
        reminders.findDueIds(clock.instant(), MAX_ATTEMPTS).forEach(dispatcher::deliver);
    }
}
