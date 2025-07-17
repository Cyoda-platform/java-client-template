package com.java_template.application.processor;

import com.java_template.application.entity.Owner;
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

@Component
public class OwnerProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public OwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("OwnerProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Owner.class)
                .withErrorHandler(this::handleOwnerError)
                .validate(Owner::isValid, "Invalid owner state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "OwnerProcessor".equals(modelSpec.operationName()) &&
                "owner".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleOwnerError(Throwable throwable, Owner owner) {
        logger.error("Error processing Owner entity", throwable);
        return new ErrorInfo("OwnerProcessingError", throwable.getMessage());
    }
}
