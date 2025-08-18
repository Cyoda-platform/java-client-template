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

@Component
public class ClassifyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ClassifyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ClassifyMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid Mail entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null;
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        try {
            String subject = mail.getSubject() == null ? "" : mail.getSubject().toLowerCase();
            String body = mail.getBody() == null ? "" : mail.getBody().toLowerCase();

            // Simple keyword based classifier
            double confidence = 0.5;
            Boolean isHappy = null;

            if (subject.contains("happy") || body.contains("happy") || body.contains("congrat")) {
                isHappy = Boolean.TRUE;
                confidence = 0.9;
            } else if (subject.contains("sad") || body.contains("sad") || body.contains("gloomy") || body.contains("unhappy")) {
                isHappy = Boolean.FALSE;
                confidence = 0.85;
            } else if (body.length() > 0 || subject.length() > 0) {
                // weak signal
                confidence = 0.55;
                isHappy = null;
            }

            mail.setIsHappy(isHappy);
            mail.setClassificationConfidence(confidence);
            // Transition to CLASSIFIED state
            try { mail.setStatus("CLASSIFIED"); } catch (Exception e) { /* ignore if setter not present */ }

            logger.info("Mail classified: isHappy={}, confidence={}", isHappy, confidence);
        } catch (Exception ex) {
            logger.error("Error classifying mail", ex);
        }
        return mail;
    }
}
