package com.java_template.application.processor;

import com.java_template.application.entity.tag.version_1.Tag;
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

/**
 * Processor for creating new tags in the system.
 * Handles the create_tag transition from initial_state to active.
 */
@Component
public class TagCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TagCreationProcessor.class);
    private final ProcessorSerializer serializer;

    public TagCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing tag creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Tag.class)
            .validate(tag -> tag.getName() != null && !tag.getName().trim().isEmpty(), 
                     "Tag name is required")
            .validate(tag -> tag.getName().length() >= 2 && tag.getName().length() <= 30, 
                     "Tag name must be between 2 and 30 characters")
            .validate(tag -> tag.getName().equals(tag.getName().toLowerCase()), 
                     "Tag name must be lowercase")
            .validate(tag -> tag.getName().matches("^[a-z0-9-]+$"), 
                     "Tag name can only contain lowercase letters, numbers, and hyphens")
            .map(context -> {
                Tag tag = context.entity();
                
                // Generate unique ID if not provided
                if (tag.getId() == null) {
                    tag.setId(System.currentTimeMillis()); // Simple ID generation
                }
                
                logger.info("Created tag with ID: {} and name: {}", tag.getId(), tag.getName());
                return tag;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "TagCreationProcessor".equals(opSpec.operationName()) &&
               "Tag".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
