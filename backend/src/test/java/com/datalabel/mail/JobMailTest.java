package com.datalabel.mail;

import com.datalabel.domain.Job;
import com.datalabel.domain.JobStatus;
import com.datalabel.mail.MailService.MailException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The invite and job-finished emails, against the fake SMTP server. */
class JobMailTest {

    private FakeSmtpServer smtp;
    private MailProperties props;
    private JobMail jobMail;

    @BeforeEach
    void start() throws Exception {
        smtp = new FakeSmtpServer();
        props = new MailProperties();
        props.setHost("127.0.0.1");
        props.setPort(smtp.port());
        props.setTls("none");
        props.setFrom("lab@example.com");
        props.setPublicUrl("https://labelhive.example.com/");
        props.setSiteName("Belmont");
        jobMail = new JobMail(new MailService(props), props);
    }

    @AfterEach
    void stop() throws Exception {
        smtp.close();
    }

    private static Job job(JobStatus status, String name) {
        Job j = new Job();
        j.setId(42L);
        j.setName(name);
        j.setStatus(status);
        return j;
    }

    @Test
    void inviteLinksToTheJobAndSaysHowToGetIn() {
        jobMail.invite(job(JobStatus.ACTIVE, "Search relevance\r\nBcc: x@evil.com"), " sam@example.com ", "Thanks for helping!", "chris");
        assertThat(smtp.messages()).hasSize(1);
        FakeSmtpServer.Message m = smtp.messages().get(0);
        assertThat(m.to()).containsExactly("sam@example.com");
        // The name's line break became a space: no injected header.
        assertThat(m.data()).doesNotContain("\r\nBcc:");
        assertThat(m.data()).contains("Subject: You're invited to label Search relevance Bcc: x@evil.com on", "LabelHive (Belmont)",
                "https://labelhive.example.com/work/42", "https://labelhive.example.com/work/42/instructions",
                "chris has invited you", "Thanks for helping!", "Access page");
    }

    @Test
    void inviteRefusesBadInput() {
        assertThatThrownBy(() -> jobMail.invite(job(JobStatus.ACTIVE, "j"), "a@example.com, b@example.com", null, null))
                .isInstanceOf(MailException.class);
        assertThatThrownBy(() -> jobMail.invite(job(JobStatus.PAUSED, "j"), "a@example.com", null, null))
                .isInstanceOf(MailException.class).hasMessageContaining("paused");
        assertThatThrownBy(() -> jobMail.invite(job(JobStatus.ACTIVE, "j"), "a@example.com", "x".repeat(JobMail.MAX_NOTE + 1), null))
                .isInstanceOf(MailException.class).hasMessageContaining("note");
        assertThat(smtp.messages()).isEmpty();
    }

    @Test
    void subjectNamesAreShortAndClean() {
        assertThat(JobMail.subjectName(null)).isEqualTo("a job");
        assertThat(JobMail.subjectName("a\tb\nc")).isEqualTo("a b c");
        assertThat(JobMail.subjectName("x".repeat(200))).hasSize(80);
    }

    @Test
    void jobFinishedGoesToNotifyToOnly() throws Exception {
        jobMail.onJobFinished(new JobMail.JobFinished(42L, "Search relevance", 120));
        Thread.sleep(200);
        assertThat(smtp.messages()).isEmpty(); // nobody to tell

        props.setNotifyTo("lead@example.com");
        jobMail.onJobFinished(new JobMail.JobFinished(42L, "Search relevance", 120));
        for (int i = 0; i < 100 && smtp.messages().isEmpty(); i++) Thread.sleep(50); // sent in the background
        assertThat(smtp.messages()).hasSize(1);
        FakeSmtpServer.Message m = smtp.messages().get(0);
        assertThat(m.to()).containsExactly("lead@example.com");
        assertThat(m.data()).contains("Subject: Job finished: Search relevance (Belmont)", "120 units",
                "https://labelhive.example.com/jobs/42/reports");
    }
}
