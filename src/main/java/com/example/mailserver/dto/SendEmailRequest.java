package com.example.mailserver.dto;

import javax.validation.constraints.NotBlank;
import java.util.Objects;

public final class SendEmailRequest {

    @NotBlank
    private String to;
    private String cc;
    private String bcc;
    @NotBlank
    private String subject;
    private String text;
    private String html;

    public SendEmailRequest() {
    }

    public SendEmailRequest(String to, String cc, String bcc, String subject, String text, String html) {
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subject = subject;
        this.text = text;
        this.html = html;
    }

    public String to() {
        return to;
    }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String cc() {
        return cc;
    }
    public String getCc() { return cc; }
    public void setCc(String cc) { this.cc = cc; }

    public String bcc() {
        return bcc;
    }
    public String getBcc() { return bcc; }
    public void setBcc(String bcc) { this.bcc = bcc; }

    public String subject() {
        return subject;
    }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String text() {
        return text;
    }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String html() {
        return html;
    }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }

    public boolean hasBody() {
        return (text != null && !text.isBlank()) || (html != null && !html.isBlank());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SendEmailRequest)) return false;
        SendEmailRequest that = (SendEmailRequest) o;
        return Objects.equals(to, that.to)
                && Objects.equals(cc, that.cc)
                && Objects.equals(bcc, that.bcc)
                && Objects.equals(subject, that.subject)
                && Objects.equals(text, that.text)
                && Objects.equals(html, that.html);
    }

    @Override
    public int hashCode() {
        return Objects.hash(to, cc, bcc, subject, text, html);
    }
}
