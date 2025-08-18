package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user for verification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && user.getTechnicalId() != null;
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();

        // Only process new registrations
        String status = user.getStatus();
        if (status == null || ("VERIFIED".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status))) {
            logger.info("User {} already verified or active - skipping verification", user.getTechnicalId());
            return user;
        }

        // Basic verification: ensure email exists
        if (user.getContactInfo() != null && user.getContactInfo().getEmail() != null && !user.getContactInfo().getEmail().trim().isEmpty()) {
            user.setStatus("VERIFIED");
            logger.info("User {} marked VERIFIED", user.getTechnicalId());
        } else {
            user.setStatus("REGISTERED");
            logger.info("User {} remains REGISTERED - missing contact email", user.getTechnicalId());
        }

        if (user.getVersion() != null) user.setVersion(user.getVersion() + 1);
        return user;
    }
}
