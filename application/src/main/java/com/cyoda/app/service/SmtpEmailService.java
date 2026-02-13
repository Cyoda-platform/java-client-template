package com.cyoda.app.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class SmtpEmailService implements EmailService {
    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailService.class);

    private final String host = System.getenv("SMTP_HOST");
    private final String port = System.getenv("SMTP_PORT");
    private final String username = System.getenv("SMTP_USERNAME");
    private final String password = System.getenv("SMTP_PASSWORD");

    @Override
    public void sendEmail(String to, String subject, String body) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props);
        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(username));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            msg.setSubject(subject);
            msg.setText(body, "utf-8");
            Transport.send(msg);
            logger.info("Sent email to {}", to);
        } catch (MessagingException e) {
            logger.error("MessagingException when sending email to {}", to, e);
            // simplistic: treat 550 errors or similar as bounce
            if (e.getMessage() != null && e.getMessage().contains("550")) {
                throw new BounceException("Remote bounce");
            }
            throw e;
        }
    }
}
