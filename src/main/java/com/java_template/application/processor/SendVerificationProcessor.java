package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.time.Instant;

@Component
public class SendVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendVerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Business logic:
        // - Send a verification challenge to the subscriber's contactDetails based on contactType.
        // - Do not mark the subscriber as verified here (verification is completed by VerificationCriterion).
        // - Update the subscriber's updatedAt timestamp and ensure active state is set (registration implies active).
        // Note: entity does not contain a verificationSentAt field, so we update updatedAt as an audit hint.

        try {
            String contactType = entity.getContactType();
            String contactDetails = entity.getContactDetails();

            if (contactType != null) {
                switch (contactType.toLowerCase()) {
                    case "email":
                        // Simulate sending an email verification challenge
                        logger.info("Sending verification email to subscriber id={}, email={}", entity.getId(), contactDetails);
                        break;
                    case "webhook":
                        // Simulate sending a webhook verification challenge
                        logger.info("Sending verification webhook to subscriber id={}, url={}", entity.getId(), contactDetails);
                        break;
                    case "sms":
                        // Simulate sending an SMS verification challenge
                        logger.info("Sending verification sms to subscriber id={}, phone={}", entity.getId(), contactDetails);
                        break;
                    default:
                        logger.warn("Unknown contactType '{}' for subscriber id={}. Will attempt generic delivery to {}",
                            contactType, entity.getId(), contactDetails);
                        break;
                }
            } else {
                logger.warn("No contactType provided for subscriber id={}. Skipping delivery.", entity.getId());
            }

            // Ensure subscriber is active after registration (consistent with API examples).
            if (!entity.isActive()) {
                entity.setActive(true);
            }

            // Do not flip verified flag here; verification response will set it.
            // Update updatedAt timestamp to indicate verification was attempted.
            entity.setUpdatedAt(Instant.now().toString());

        } catch (Exception ex) {
            // Log the error but do not fail the processor; keep entity state consistent.
            logger.error("Failed while sending verification for subscriber id={}: {}", entity.getId(), ex.getMessage(), ex);
            // Update updatedAt to reflect the attempt and preserve current verified/active flags.
            entity.setUpdatedAt(Instant.now().toString());
        }

        return entity;
    }
}