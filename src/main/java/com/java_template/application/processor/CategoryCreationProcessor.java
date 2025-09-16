package com.java_template.application.processor;

import com.java_template.application.entity.category.version_1.Category;
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
 * Processor for creating new categories in the system.
 * Handles the create_category transition from initial_state to active.
 */
@Component
public class CategoryCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CategoryCreationProcessor.class);
    private final ProcessorSerializer serializer;

    public CategoryCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing category creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Category.class)
            .validate(category -> category.getName() != null && !category.getName().trim().isEmpty(), 
                     "Category name is required")
            .validate(category -> category.getName().length() >= 2 && category.getName().length() <= 50, 
                     "Category name must be between 2 and 50 characters")
            .map(context -> {
                Category category = context.entity();
                
                // Generate unique ID if not provided
                if (category.getId() == null) {
                    category.setId(System.currentTimeMillis()); // Simple ID generation
                }
                
                logger.info("Created category with ID: {} and name: {}", category.getId(), category.getName());
                return category;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CategoryCreationProcessor".equals(opSpec.operationName()) &&
               "Category".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
