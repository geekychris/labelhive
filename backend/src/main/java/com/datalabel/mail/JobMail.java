package com.datalabel.mail;

import com.datalabel.domain.Job;
import com.datalabel.domain.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LabelHive's two emails: an admin invites someone to label a job (a link
 * to the job's worker view), and the configured address hears when a job's
 * last unit is done. Neither grants access: sign-in and roles stay with the
 * site's login service, so the invite says how to get in.
 */
@Service
public class JobMail {

    private static final Logger log = LoggerFactory.getLogger(JobMail.class);
    /** Longest admin note kept in an invite. */
    static final int MAX_NOTE = 1000;

    /** Published (by JudgmentService) when a job's last open non-gold unit completes. */
    public record JobFinished(Long jobId, String jobName, long units) { }

    private final MailService mail;
    private final MailProperties props;
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "job-mail");
        t.setDaemon(true);
        return t;
    });

    public JobMail(MailService mail, MailProperties props) {
        this.mail = mail;
        this.props = props;
    }

    /** A job's name made safe for a subject line: no control characters, at most 80 characters. */
    static String subjectName(String name) {
        String n = name == null ? "" : name.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) n = "a job";
        return n.length() > 80 ? n.substring(0, 79) + "…" : n;
    }

    private String site() {
        return props.getSiteName().isEmpty() ? "" : " (" + props.getSiteName() + ")";
    }

    /** Emails an invite to label {@code job} to one address. Throws MailService.MailException. */
    public void invite(Job job, String to, String note, String invitedBy) {
        String addr = MailService.checkAddress(to);
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new MailService.MailException(MailService.MailException.Kind.INVALID,
                    "the job is " + job.getStatus().name().toLowerCase() + "; only an active job takes invites");
        }
        String n = note == null ? "" : note.strip();
        if (n.length() > MAX_NOTE) {
            throw new MailService.MailException(MailService.MailException.Kind.INVALID, "the note is longer than " + MAX_NOTE + " characters");
        }
        String base = props.getPublicUrl();
        String name = subjectName(job.getName());
        StringBuilder b = new StringBuilder();
        b.append("Hello,\n\n")
         .append(invitedBy == null || invitedBy.isBlank() ? "You've" : invitedBy.strip() + " has")
         .append(" invited you to help label \"").append(name).append("\" on LabelHive").append(site()).append(".\n\n");
        if (!n.isEmpty()) b.append(n).append("\n\n");
        b.append("Start labeling:\n  ").append(base).append("/work/").append(job.getId()).append("\n\n")
         .append("Read the instructions first:\n  ").append(base).append("/work/").append(job.getId()).append("/instructions\n\n")
         .append("You sign in with the site's passkey sign-in. If it says you don't have access to LabelHive,\n")
         .append("reply to the person who invited you: they give access on the site's Access page.\n\n")
         .append("-- \nLabelHive").append(site()).append("\n");
        mail.send(addr, "You're invited to label " + name + " on LabelHive" + site(), b.toString());
        log.info("job invite sent job={} to={}", job.getId(), addr);
    }

    /** After the judgment that finished a job is committed, tell notify-to (in the background; a failure is only logged). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobFinished(JobFinished e) {
        if (props.getNotifyTo().isEmpty() || !mail.isEnabled()) return;
        background.submit(() -> {
            try {
                String name = subjectName(e.jobName());
                String base = props.getPublicUrl();
                String text = "Every unit of \"" + name + "\" now has its judgments (" + e.units() + " units).\n\n"
                        + "Reports:\n  " + base + "/jobs/" + e.jobId() + "/reports\n"
                        + "Export:\n  " + base + "/jobs/" + e.jobId() + "\n\n"
                        + "-- \nLabelHive" + site() + "\n";
                mail.send(props.getNotifyTo(), "Job finished: " + name + site(), text);
            } catch (RuntimeException ex) {
                log.warn("job-finished mail not sent job={}: {}", e.jobId(), ex.getMessage());
            }
        });
    }
}
