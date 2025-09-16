package com.java_template.application.processor;

import com.java_template.application.entity.category.version_1.Category;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * CategoryActivationProcessor - Activate category and set default values
 * 
 * Transition: activate_category (none → active)
 * Purpose: Activate category and set default values
 */
@Component
public class CategoryActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CategoryActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CategoryActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Category activation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Category.class)
                .validate(this::isValidEntityWithMetadata, "Invalid category entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Category> entityWithMetadata) {
        Category entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Category> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Category> context) {

        EntityWithMetadata<Category> entityWithMetadata = context.entityResponse();
        Category category = entityWithMetadata.entity();

        logger.debug("Processing category activation: {}", category.getCategoryId());

        // 1. Validate category has required fields (name) - already done in isValid()
        
        // 2. Set default values
        if (category.getCreatedAt() == null) {
            category.setCreatedAt(LocalDateTime.now());
        }
        if (category.getDescription() == null || category.getDescription().trim().isEmpty()) {
            category.setDescription("No description available");
        }

        // 3. Set updatedAt = current timestamp
        category.setUpdatedAt(LocalDateTime.now());

        logger.info("Category {} activated successfully", category.getCategoryId());
        return entityWithMetadata;
    }
}
