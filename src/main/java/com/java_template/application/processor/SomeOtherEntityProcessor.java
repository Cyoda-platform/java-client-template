package com.java_template.application.processor;

import com.java_template.application.entity.SomeOtherEntity;
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
public class SomeOtherEntityProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public SomeOtherEntityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("SomeOtherEntityProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SomeOtherEntity for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(SomeOtherEntity.class)
                .withErrorHandler(this::handleSomeOtherEntityError)
                .validate(this::isValidSomeOtherEntity, "Invalid someOtherEntity state")
                .map(this::applySomeOtherEntityLogic)
                .validate(this::businessValidation, "Failed someOtherEntity business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SomeOtherEntityProcessor".equals(modelSpec.operationName()) &&
               "someOtherEntity".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidSomeOtherEntity(SomeOtherEntity entity) {
        return entity != null && entity.isValid();
    }

    private ErrorInfo handleSomeOtherEntityError(Throwable throwable, SomeOtherEntity entity) {
        logger.error("Error processing SomeOtherEntity", throwable);
        return new ErrorInfo("SomeOtherEntity_processing_error", throwable.getMessage());
    }

    private SomeOtherEntity applySomeOtherEntityLogic(SomeOtherEntity entity) {
        // Placeholder for business logic
        return entity;
    }

    private boolean businessValidation(SomeOtherEntity entity) {
        // Placeholder for business validation
        return true;
    }
}
