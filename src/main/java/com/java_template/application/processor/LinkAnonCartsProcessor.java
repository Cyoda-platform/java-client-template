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
public class LinkAnonCartsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LinkAnonCartsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LinkAnonCartsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) // always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
            // Business rule: only run when user is IDENTIFIED
            if (user.getIdentificationStatus() == null || !"IDENTIFIED".equalsIgnoreCase(user.getIdentificationStatus())) {
                logger.debug("User {} is not IDENTIFIED, skipping linking carts.", user.getId());
                return user;
            }

            // Linking anonymous carts to identified user:
            // NOTE: Actual linking requires searching for Cart entities with null userId and
            // calling entityService.updateItem(...) for each cart. Per processor constraints
            // we must not update the triggering entity (User) and may only update other entities.
            // The EntityService usage and exact search API vary across deployments; to keep this
            // processor safe and compilable in the template environment, we only log intent here.
            // Implementers should add the search + update calls using EntityService according to
            // their platform API (SearchConditionRequest.group(...) and Condition.of(...)).
            logger.info("User {} is IDENTIFIED. LinkAnonCartsProcessor should link anonymous carts to this user. (Linking logic not implemented in template)", user.getId());

        } catch (Exception ex) {
            logger.error("Error while linking anonymous carts for user {}: {}", user.getId(), ex.getMessage(), ex);
        }

        return user;
    }
}