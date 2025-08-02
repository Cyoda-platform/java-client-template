package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
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
import java.util.UUID;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        String technicalIdStr = context.request().getEntityId();
        UUID technicalId = null;
        try {
            technicalId = UUID.fromString(technicalIdStr);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}. Setting mail status to FAILED.", technicalIdStr);
            mail.setStatus("FAILED");
            return mail;
        }

        if (mail.getIsHappy() != null) {
            if (mail.getIsHappy()) {
                logger.info("Processing happy mail for technicalId: {}", technicalId);
                try {
                    // Simulate sending happy mail to external service
                    logger.info("Simulating sending happy mail to: {}", mail.getMailList());
                    // In a full implementation, this would involve an actual external API call.
                    mail.setStatus("SENT_HAPPY");
                    logger.info("Simulated happy mail sent successfully for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
                } catch (Exception e) {
                    logger.error("Failed to simulate sending happy mail for technicalId: {}. Error: {}", technicalId, e.getMessage());
                    mail.setStatus("FAILED");
                    logger.error("Simulated happy mail failed for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
                }
            } else { // isHappy is false
                logger.info("Processing gloomy mail for technicalId: {}", technicalId);
                try {
                    // Simulate sending gloomy mail to external service
                    logger.info("Simulating sending gloomy mail to: {}", mail.getMailList());
                    // In a full implementation, this would involve an actual external API call.
                    mail.setStatus("SENT_GLOOMY");
                    logger.info("Simulated gloomy mail sent successfully for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
                } catch (Exception e) {
                    logger.error("Failed to simulate sending gloomy mail for technicalId: {}. Error: {}", technicalId, e.getMessage());
                    mail.setStatus("FAILED");
                    logger.error("Simulated gloomy mail failed for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
                }
            }
        } else {
            logger.warn("Mail technicalId: {} has an undefined 'isHappy' status. Setting to FAILED.", technicalId);
            mail.setStatus("FAILED");
        }
        return mail;
    }
}