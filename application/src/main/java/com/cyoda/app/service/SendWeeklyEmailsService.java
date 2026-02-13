package com.cyoda.app.service;

import com.cyoda.app.entity.CatFact;
import com.cyoda.app.entity.Subscriber;
import com.cyoda.app.repository.SubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SendWeeklyEmailsService {
    private static final Logger logger = LoggerFactory.getLogger(SendWeeklyEmailsService.class);
    private final SubscriberRepository subscriberRepository;
    private final EmailService emailService;
    private final ReportingService reportingService;

    public SendWeeklyEmailsService(SubscriberRepository subscriberRepository,
                                   EmailService emailService,
                                   ReportingService reportingService) {
        this.subscriberRepository = subscriberRepository;
        this.emailService = emailService;
        this.reportingService = reportingService;
    }

    public void sendEmailsForFact(CatFact fact) {
        logger.info("Starting to send weekly emails for cat fact id={}", fact.getId());
        List<Subscriber> subscribers = subscriberRepository.findByUnsubscribedFalse();
        int sent = 0;
        int bounces = 0;
        int failures = 0;

        for (Subscriber s : subscribers) {
            boolean delivered = false;
            int attempts = 0;
            long backoff = 1000L; // start 1s
            while (!delivered && attempts < 3) {
                attempts++;
                try {
                    emailService.sendEmail(s.getEmail(), "Your Weekly Cat Fact", fact.getText());
                    delivered = true;
                    sent++;
                } catch (EmailService.BounceException be) {
                    bounces++;
                    logger.warn("Bounce when sending to {}: {}", s.getEmail(), be.getMessage());
                    break; // don't retry bounces
                } catch (Exception e) {
                    failures++;
                    logger.warn("Failed attempt {} sending to {}: {}", attempts, s.getEmail(), e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ignored) {
                    }
                    backoff *= 2; // exponential
                }
            }
        }

        LocalDate weekStart = LocalDate.now();
        reportingService.recordWeeklyReport(weekStart, subscribers.size(), sent, bounces, failures);
        logger.info("Weekly send completed: subscribers={}, sent={}, bounces={}, failures={}", subscribers.size(), sent, bounces, failures);
    }
}
