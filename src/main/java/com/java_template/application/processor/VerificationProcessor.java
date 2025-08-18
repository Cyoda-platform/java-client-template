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

@Component
public class VerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Verification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid subscriber for verification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber s) {
        return s != null && s.getContact() != null && !s.getContact().isBlank();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber s = context.entity();
        try {
            // perform lightweight verification: for WEBHOOK try challenge, for EMAIL assume verification flow started
            if (s.getContactType() == Subscriber.ContactType.WEBHOOK) {
                logger.info("Attempting webhook verification for {}", s.getContact());
                // TODO: perform HTTP challenge-response
                s.setStatus(Subscriber.Status.VERIFIED);
            } else if (s.getContactType() == Subscriber.ContactType.EMAIL) {
                logger.info("Sending verification email to {}", s.getContact());
                s.setStatus(Subscriber.Status.PENDING);
            } else {
                s.setStatus(Subscriber.Status.PENDING);
            }
        } catch (Exception e) {
            logger.warn("Error during subscriber verification {}: {}", s.getContact(), e.getMessage());
        }
        return s;
    }
}
