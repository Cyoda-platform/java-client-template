package com.java_template.application.processor;

import com.java_template.application.entity.Category;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class CategoryProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public CategoryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("CategoryProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Category for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Category.class)
                .withErrorHandler(this::handleCategoryError)
                .validate(this::isValidCategory, "Invalid category state")
                .map(this::applyBusinessLogic)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CategoryProcessor".equals(modelSpec.operationName()) &&
                "category".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidCategory(Category category) {
        return category.getId() != null && category.getName() != null && !category.getName().isEmpty();
    }

    private ErrorInfo handleCategoryError(Throwable throwable, Category category) {
        logger.error("Error processing Category: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("CategoryProcessingError", throwable.getMessage());
    }

    private Category applyBusinessLogic(Category category) {
        // Example business logic: if category name contains 'deprecated', mark name as 'deprecated'
        if (category.getName() != null && category.getName().toLowerCase().contains("deprecated")) {
            category.setName("deprecated");
        }
        return category;
    }

    private boolean businessValidation(Category category) {
        // Example business validation: name should not be empty
        return category.getName() != null && !category.getName().isEmpty();
    }

}
