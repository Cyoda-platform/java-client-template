package com.java_template.application.processor;

import com.java_template.application.entity.userrecord.version_1.UserRecord;
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

import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public ValidateUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateUserProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(UserRecord.class)
            .validate(this::isValidEntity, "Invalid user record")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(UserRecord user) {
        return user != null && user.isValid();
    }

    private UserRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserRecord> context) {
        UserRecord user = context.entity();
        try {
            if (user.getEmail() == null || user.getEmail().isBlank() || !EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
                user.setStatus("ERROR");
                user.setErrorMessage("Invalid or missing email");
                logger.info("UserRecord externalId={} failed validation: invalid email", user.getExternalId());
                return user;
            }

            if (user.getFirstName() == null || user.getFirstName().isBlank()) {
                user.setStatus("ERROR");
                user.setErrorMessage("Missing required firstName");
                return user;
            }

            if (user.getLastName() == null || user.getLastName().isBlank()) {
                user.setStatus("ERROR");
                user.setErrorMessage("Missing required lastName");
                return user;
            }

            user.setNormalized(true);
            user.setStatus("VERIFIED");
            logger.info("UserRecord externalId={} verified", user.getExternalId());
            return user;
        } catch (Exception ex) {
            logger.error("Unexpected error during ValidateUserProcessor", ex);
            user.setStatus("ERROR");
            user.setErrorMessage("Validation error: " + ex.getMessage());
            return user;
        }
    }
}
