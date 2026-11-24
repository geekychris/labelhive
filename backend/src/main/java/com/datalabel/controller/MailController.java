package com.datalabel.controller;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Job;
import com.datalabel.mail.JobMail;
import com.datalabel.mail.MailService;
import com.datalabel.mail.MailService.MailException;
import com.datalabel.repository.JobRepository;
import com.datalabel.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Email (admins only, like all of /api/**): whether mail is set up, and
 * inviting someone to label a job.
 */
@RestController
public class MailController {

    private final MailService mail;
    private final JobMail jobMail;
    private final JobRepository jobs;
    private final CurrentUser currentUser;

    public MailController(MailService mail, JobMail jobMail, JobRepository jobs, CurrentUser currentUser) {
        this.mail = mail;
        this.jobMail = jobMail;
        this.jobs = jobs;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/mail/status")
    public Map<String, Object> status() {
        String reason = mail.unavailableReason();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", reason == null);
        if (reason != null) m.put("reason", reason);
        return m;
    }

    public record InviteBody(String to, String note) { }

    @PostMapping("/api/jobs/{id}/invite")
    public ResponseEntity<Map<String, Object>> invite(@PathVariable Long id, @RequestBody InviteBody body, HttpServletRequest req) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) return error(HttpStatus.NOT_FOUND, "no such job");
        String by = null;
        try {
            AppUser u = currentUser.require(req);
            by = u.getUsername();
        } catch (RuntimeException ignored) {
            // the invite still goes, without a name
        }
        try {
            jobMail.invite(job, body == null ? null : body.to(), body == null ? null : body.note(), by);
            return ResponseEntity.ok(Map.of("sent", true, "to", MailService.checkAddress(body.to())));
        } catch (MailException e) {
            HttpStatus s = switch (e.getKind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                case FAILED -> HttpStatus.BAD_GATEWAY;
            };
            return error(s, e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(Map.of("sent", false, "error", msg));
    }
}
