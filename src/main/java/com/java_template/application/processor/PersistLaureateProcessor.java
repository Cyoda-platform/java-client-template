package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public PersistLaureateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Persisting laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate entity")
            .map(this::persistLaureate)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null
                && laureate.getFirstname() != null && !laureate.getFirstname().isEmpty()
                && laureate.getSurname() != null && !laureate.getSurname().isEmpty()
                && laureate.getYear() != null && !laureate.getYear().isEmpty()
                && laureate.getCategory() != null && !laureate.getCategory().isEmpty();
    }

    private Laureate persistLaureate(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                laureate
            );
            UUID id = idFuture.get();
            logger.info("Persisted Laureate with generated id: {}", id);
        } catch (Exception e) {
            logger.error("Error persisting Laureate", e);
        }
        return laureate;
    }
}
