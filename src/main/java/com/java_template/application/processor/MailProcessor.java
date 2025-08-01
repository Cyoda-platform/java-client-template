package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Mail;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Random;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public MailProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid mail entity state")
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
        
        logger.info("MailProcessor: Starting processing for mail: {}", technicalId);

        // 1. Criteria Evaluation: Determine isHappy
        System.out.println("MailProcessor: Evaluating criteria for mail: " + technicalId + " (Current Status: " + mail.getStatus() + ")");
        boolean happy = (mail.getMailList() != null && mail.getMailList().size() % 2 == 0) ||
                        (mail.getMailList() != null && mail.getMailList().size() > 2);
        
        mail.setIsHappy(happy);
        System.out.println("MailProcessor: Mail " + technicalId + " \'isHappy\' set to: " + mail.getIsHappy());
        mail.setStatus("CRITERIA_EVALUATED");

        // 2. Processor Invocation: Send Happy or Gloomy Mail
        try {
            if (mail.getIsHappy() != null && mail.getIsHappy()) {
                System.out.println("MailProcessor: Attempting to send happy mail for: " + technicalId);
                if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                    throw new RuntimeException("No recipients provided for happy mail.");
                }
                if (new Random().nextInt(10) == 0) {
                    throw new RuntimeException("Simulated network error during happy mail send.");
                }
                Thread.sleep(500);
                mail.setStatus("HAPPY_MAIL_SENT");
                System.out.println("MailProcessor: Successfully sent happy mail for: " + technicalId);
            } else {
                System.out.println("MailProcessor: Attempting to send gloomy mail for: " + technicalId);
                if (mail.getMailList() != null && mail.getMailList().size() == 1) {
                    throw new RuntimeException("Gloomy mail cannot be sent to only one recipient (too sad!).");
                }
                if (new Random().nextInt(10) == 0) {
                    throw new RuntimeException("Simulated external service timeout for gloomy mail.");
                }
                Thread.sleep(500);
                mail.setStatus("GLOOMY_MAIL_SENT");
                System.out.println("MailProcessor: Successfully sent gloomy mail for: " + technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            System.err.println("MailProcessor: Failed to send mail for " + technicalId + ": " + e.getMessage());
            throw new RuntimeException("Mail Sending Failed", e);
        }

        return mail;
    }
}