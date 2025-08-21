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
public class ApplyPreferencesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApplyPreferencesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ApplyPreferencesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ApplyPreferences for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        try {
            // Ensure user-level default preferences fields are present and sensible
            if (user.getDefaultBoilType() == null || user.getDefaultBoilType().isBlank()) {
                user.setDefaultBoilType("medium");
            }
            if (user.getDefaultEggSize() == null || user.getDefaultEggSize().isBlank()) {
                user.setDefaultEggSize("medium");
            }
            if (user.getAllowMultipleTimers() == null) {
                user.setAllowMultipleTimers(Boolean.TRUE);
            }

            logger.info("Applied normalized preferences for user {}: boilType={}, eggSize={}, allowMultipleTimers={}",
                user.getId(), user.getDefaultBoilType(), user.getDefaultEggSize(), user.getAllowMultipleTimers());
        } catch (Exception ex) {
            logger.error("Error in ApplyPreferencesProcessor for user {}: {}", user != null ? user.getId() : null, ex.getMessage(), ex);
        }
        return user;
    }
}
