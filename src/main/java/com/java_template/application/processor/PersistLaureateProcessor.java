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

import java.util.UUID;
import java.util.concurrent.CompletionException;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistLaureateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistLaureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate state for persistence")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        try {
            // Persist laureate using entityService.addItem which returns technicalId
            UUID technicalId = entityService.addItem(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                laureate
            ).join();

            // Optionally store the technical id in one of the laureate fields if needed
            // We can't set a dedicated technicalId field because entity POJO does not define it; leaving as-is
            laureate.setStatus("STORED");
            logger.info("Laureate persisted id={} technicalId={}", laureate.getId(), technicalId);
        } catch (CompletionException ce) {
            logger.error("Error persisting laureate id={}: {}", laureate.getId(), ce.getMessage(), ce);
            laureate.setStatus("REJECTED");
            laureate.setLastError(ce.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error persisting laureate id={}: {}", laureate.getId(), e.getMessage(), e);
            laureate.setStatus("REJECTED");
            laureate.setLastError(e.getMessage());
        }
        return laureate;
    }
}
