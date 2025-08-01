package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
import com.java_template.application.entity.HappyMailJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public MailProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(this::isValidEntity, "Invalid Mail entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        String technicalId = context.request().getEntityId();

        logger.info("Processing Mail entity with technicalId {}", technicalId);

        // Validate mailList not empty and emails valid
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            logger.error("Mail list is empty for Mail technicalId {}", technicalId);
            createAndStoreJob(technicalId, "FAILED", "Mail list is empty");
            return mail;
        }

        for (String email : mail.getMailList()) {
            if (!isValidEmail(email)) {
                logger.error("Invalid email '{}' in Mail technicalId {}", email, technicalId);
                createAndStoreJob(technicalId, "FAILED", "Invalid email address: " + email);
                return mail;
            }
        }

        // Route to appropriate processor based on isHappy flag
        if (mail.getIsHappy() != null && mail.getIsHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }

        return mail;
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending happy mails for Mail technicalId {}", technicalId);

        // Simulate sending mails with happy content
        boolean success = simulateMailSending(mail.getMailList(), "Happy mail content");

        if (success) {
            createAndStoreJob(technicalId, "COMPLETED", "Happy mails sent successfully");
            logger.info("Happy mails sent successfully for Mail technicalId {}", technicalId);
        } else {
            createAndStoreJob(technicalId, "FAILED", "Failed to send happy mails");
            logger.error("Failed to send happy mails for Mail technicalId {}", technicalId);
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending gloomy mails for Mail technicalId {}", technicalId);

        // Simulate sending mails with gloomy content
        boolean success = simulateMailSending(mail.getMailList(), "Gloomy mail content");

        if (success) {
            createAndStoreJob(technicalId, "COMPLETED", "Gloomy mails sent successfully");
            logger.info("Gloomy mails sent successfully for Mail technicalId {}", technicalId);
        } else {
            createAndStoreJob(technicalId, "FAILED", "Failed to send gloomy mails");
            logger.error("Failed to send gloomy mails for Mail technicalId {}", technicalId);
        }
    }

    private boolean simulateMailSending(List<String> recipients, String content) {
        // Simulate mail sending; always succeed for prototype
        logger.info("Simulating sending mail to recipients: {} with content: {}", recipients, content);
        return true;
    }

    private void createAndStoreJob(String mailTechnicalId, String status, String resultMessage) {
        HappyMailJob job = new HappyMailJob();
        job.setMailTechnicalId(mailTechnicalId);
        job.setStatus(status);
        job.setCreatedAt(LocalDateTime.now());
        job.setResultMessage(resultMessage);

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(HappyMailJob.ENTITY_NAME, "1", job);
            UUID jobId = idFuture.get();
            logger.info("Created HappyMailJob with id {} for Mail technicalId {} with status {}", jobId, mailTechnicalId, status);

            // processHappyMailJob(jobId.toString(), job); // Not implemented here
        } catch (Exception e) {
            logger.error("Error creating HappyMailJob for Mail technicalId {}", mailTechnicalId, e);
        }
    }

    private boolean isValidEmail(String email) {
        // Simple email validation
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
