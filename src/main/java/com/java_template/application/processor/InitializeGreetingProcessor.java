package com.java_template.application.processor;

import com.java_template.application.entity.greeting.version_1.Greeting;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;

/**
 * InitializeGreetingProcessor - Ensures timestamp exists (uses system local time if absent)
 */
@Component
public class InitializeGreetingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitializeGreetingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public InitializeGreetingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InitializeGreeting for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Greeting.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Greeting> entityWithMetadata) {
        Greeting entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Greeting> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Greeting> context) {

        EntityWithMetadata<Greeting> entityWithMetadata = context.entityResponse();
        Greeting entity = entityWithMetadata.entity();

        logger.debug("Processing greeting: {} in state: {}", entity.getGreetingId(), entityWithMetadata.metadata().getState());

        // If timestamp is not set, use current system time
        if (entity.getTimestamp() == null) {
            entity.setTimestamp(LocalDateTime.now());
            logger.info("Initialized timestamp with system local time: {}", entity.getTimestamp());
        }

        logger.info("Greeting {} initialized successfully", entity.getGreetingId());

        return entityWithMetadata;
    }
}

