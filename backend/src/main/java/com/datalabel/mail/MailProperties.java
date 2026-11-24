package com.datalabel.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Outgoing mail for job invites and job-finished notices (datalabel.mail.*, or
 * the environment: LABELHIVE_MAIL_HOST, LABELHIVE_MAIL_PORT, ...).
 *
 * The password is not a property: it is read from {@link #passwordFile} for
 * each send (a Kubernetes Secret mounted as a file), so it never sits in the
 * environment, the configuration or a log, and placing it needs no restart.
 * With no host, or an account but no password, mail is off and the job page
 * hides the email box.
 */
@Component
@ConfigurationProperties(prefix = "datalabel.mail")
public class MailProperties {

    /** SMTP server, e.g. smtp.gmail.com. Empty: mail is off. */
    private String host = "";
    /** 587 for STARTTLS, 465 for TLS. 0: from the TLS mode. */
    private int port = 0;
    /** starttls (default), tls, or none (a test catcher such as Mailpit; no sign-in then). */
    private String tls = "starttls";
    /** Account to sign in as; empty: no sign-in (only with tls none). */
    private String username = "";
    /** Sender address; empty: the account. Gmail keeps another From only if it is a verified "Send mail as" alias. */
    private String from = "";
    /** Display name shown with From. */
    private String fromName = "LabelHive";
    /** Where replies go, optional. */
    private String replyTo = "";
    /** The file holding the password. */
    private String passwordFile = "/var/run/labelhive/smtp/password";
    /** The site's everyday name for the message ("Belmont"), optional. */
    private String siteName = "";
    /** The app's public address (https://labelhive.hitorro.com), for links in the mail. */
    private String publicUrl = "";
    /** Who hears when a job's last unit is done; empty: nobody. One plain address. */
    private String notifyTo = "";
    /** Emails in an hour, in total and to one address. */
    private int ratePerHour = 30;
    private int ratePerRecipientPerHour = 5;
    /** Connection and read timeout, milliseconds. */
    private int timeoutMillis = 20000;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = trim(host); }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getTls() { return tls; }
    public void setTls(String tls) { this.tls = trim(tls).toLowerCase(); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = trim(username); }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = trim(from); }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = trim(fromName); }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = trim(replyTo); }
    public String getPasswordFile() { return passwordFile; }
    public void setPasswordFile(String passwordFile) { this.passwordFile = trim(passwordFile); }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = trim(siteName); }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) {
        String u = trim(publicUrl);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.publicUrl = u;
    }
    public String getNotifyTo() { return notifyTo; }
    public void setNotifyTo(String notifyTo) { this.notifyTo = trim(notifyTo); }
    public int getRatePerHour() { return ratePerHour; }
    public void setRatePerHour(int ratePerHour) { this.ratePerHour = ratePerHour; }
    public int getRatePerRecipientPerHour() { return ratePerRecipientPerHour; }
    public void setRatePerRecipientPerHour(int v) { this.ratePerRecipientPerHour = v; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }

    /** The port, filled from the TLS mode when unset. */
    public int effectivePort() {
        if (port > 0) return port;
        return switch (tls) {
            case "tls" -> 465;
            case "none" -> 25;
            default -> 587;
        };
    }

    /** The sender address: From, or else the account. */
    public String effectiveFrom() {
        return from.isEmpty() ? username : from;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
