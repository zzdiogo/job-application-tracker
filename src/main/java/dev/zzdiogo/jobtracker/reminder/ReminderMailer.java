package dev.zzdiogo.jobtracker.reminder;

import dev.zzdiogo.jobtracker.application.JobApplication;
import dev.zzdiogo.jobtracker.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends reminder emails. Email is optional: without SPRING_MAIL_HOST there is no sender. */
@Component
public class ReminderMailer {

    private final JavaMailSender sender;
    private final AppProperties properties;

    public ReminderMailer(ObjectProvider<JavaMailSender> sender, AppProperties properties) {
        this.sender = sender.getIfAvailable();
        this.properties = properties;
    }

    public boolean isConfigured() {
        return sender != null;
    }

    public void send(Reminder reminder) {
        JobApplication application = reminder.getApplication();
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mailFrom());
        mail.setTo(application.getUser().getEmail());
        mail.setSubject(oneLine("Reminder: " + application.getRole() + " at " + application.getCompany()));
        mail.setText(body(reminder, application));
        sender.send(mail);
    }

    private static String body(Reminder reminder, JobApplication application) {
        StringBuilder body = new StringBuilder(reminder.getMessage())
                .append("\n\nCompany: ").append(application.getCompany())
                .append("\nRole: ").append(application.getRole())
                .append("\nStatus: ").append(application.getStatus());
        if (application.getUrl() != null) {
            body.append("\nLink: ").append(application.getUrl());
        }
        return body.toString();
    }

    /** User text ends up in a mail header, where a line break could inject extra headers. */
    private static String oneLine(String text) {
        return text.replaceAll("\\p{Cntrl}+", " ");
    }
}
