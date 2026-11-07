package dev.zzdiogo.jobtracker;

import static org.assertj.core.api.Assertions.assertThatCode;

import dev.zzdiogo.jobtracker.reminder.ReminderScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NoMailConfiguredTest extends ApiTestSupport {

    @Autowired
    ReminderScheduler scheduler;

    @Test
    void schedulerDoesNothingWithoutAMailServer() {
        assertThatCode(scheduler::sendDueReminders).doesNotThrowAnyException();
    }
}
