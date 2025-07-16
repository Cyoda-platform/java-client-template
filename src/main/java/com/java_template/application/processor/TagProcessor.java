package com.java_template.application.processor;

import com.java_template.application.entity.Tag;
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
public class TagProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public TagProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("TagProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Tag for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Tag.class)
                .withErrorHandler(this::handleTagError)
                .validate(this::isValidTag, "Invalid tag state")
                .map(this::applyBusinessLogic)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "TagProcessor".equals(modelSpec.operationName()) &&
                "tag".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidTag(Tag tag) {
        return tag.getId() != null && tag.getName() != null && !tag.getName().isEmpty();
    }

    private ErrorInfo handleTagError(Throwable throwable, Tag tag) {
        logger.error("Error processing Tag: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("TagProcessingError", throwable.getMessage());
    }

    private Tag applyBusinessLogic(Tag tag) {
        // Example business logic: capitalize tag name
        if (tag.getName() != null) {
            tag.setName(tag.getName().toUpperCase());
        }
        return tag;
    }

    private boolean businessValidation(Tag tag) {
        // Example business validation: name should not be empty
        return tag.getName() != null && !tag.getName().isEmpty();
    }

}
