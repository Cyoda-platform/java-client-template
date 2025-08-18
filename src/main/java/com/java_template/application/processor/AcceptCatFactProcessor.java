package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class AcceptCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AcceptCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    private static final int MIN_LENGTH = 20; // from requirements

    public AcceptCatFactProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AcceptCatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact catFact = context.entity();
        try {
            if (catFact.getText() == null || catFact.getText().trim().length() < MIN_LENGTH) {
                catFact.setStatus("rejected");
                logger.info("CatFact {} rejected due to insufficient length", catFact.getId());
                return catFact;
            }

            // mark ready
            catFact.setStatus("ready");
            catFact.setArchived(false);
            logger.info("CatFact {} accepted and ready", catFact.getId());

        } catch (Exception ex) {
            logger.error("Error accepting cat fact {}: {}", catFact.getId(), ex.getMessage(), ex);
            catFact.setStatus("rejected");
        }
        return catFact;
    }
}
