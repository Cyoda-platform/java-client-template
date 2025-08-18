package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
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

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

@Component
public class EvaluateMailCriteriaProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EvaluateMailCriteriaProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private static final Pattern EMAIL_REGEX = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int DEFAULT_MAX_RETRIES = 3;

    public EvaluateMailCriteriaProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail evaluation for request: {}", request.getId());

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

    private boolean isValidEntity(Mail mail) {
        if (mail == null) return false;
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) return false;
        // ensure addresses are non-blank (entity.isValid covers null/blank) but keep defensive check
        for (String addr : mail.getMailList()) {
            if (addr == null || addr.isBlank()) return false;
        }
        // attemptCount must be non-negative if present
        if (mail.getAttemptCount() != null && mail.getAttemptCount() < 0) return false;
        return true;
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        // Ensure attemptCount default
        if (mail.getAttemptCount() == null) {
            mail.setAttemptCount(0);
        }

        // Validate email formats before evaluation. If any recipient has permanently invalid format, mark FAILED.
        for (String recipient : mail.getMailList()) {
            if (recipient == null || !EMAIL_REGEX.matcher(recipient).matches()) {
                logger.error("Invalid email detected during evaluation for mail {}: {}", mail.getTechnicalId(), recipient);
                mail.setStatus("FAILED");
                mail.setLastAttemptAt(OffsetDateTime.now());
                return mail;
            }
        }

        // Business logic: If isHappy explicitly provided by the client, honor it. Otherwise, deterministically evaluate
        // based on recipient addresses: if any recipient contains the substring "happy" (case-insensitive),
        // mark as happy.

        Boolean provided = mail.getIsHappy();
        if (provided == null) {
            boolean happy = mail.getMailList().stream()
                .filter(e -> e != null)
                .anyMatch(e -> e.toLowerCase().contains("happy"));
            mail.setIsHappy(happy);
            logger.debug("IsHappy not provided; evaluated to {} for mail {}", happy, mail.getTechnicalId());
        } else {
            logger.debug("IsHappy provided by client as {} for mail {}", provided, mail.getTechnicalId());
        }

        // After evaluation, transition to READY_TO_SEND
        mail.setStatus("READY_TO_SEND");
        return mail;
    }
}
