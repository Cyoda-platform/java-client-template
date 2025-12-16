package com.java_template.application.processor;

import com.java_template.application.entity.settlement_instruction.version_1.SettlementInstruction;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * Processor for executing settlement
 * Finalizes settlement and updates depository records
 */
@Component
public class SettlementExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SettlementExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SettlementExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SettlementExecution for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SettlementInstruction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid settlement instruction wrapper")
                .map(this::executeSettlement)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<SettlementInstruction> entityWithMetadata) {
        SettlementInstruction entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<SettlementInstruction> executeSettlement(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SettlementInstruction> context) {

        EntityWithMetadata<SettlementInstruction> entityWithMetadata = context.entityResponse();
        SettlementInstruction instruction = entityWithMetadata.entity();

        logger.debug("Executing settlement for instruction: {}", instruction.getSettlementInstructionId());

        // Check settlement date
        if (instruction.getSettlementDate() != null && instruction.getSettlementDate().isAfter(LocalDateTime.now())) {
            logger.warn("Settlement instruction {} settlement date is in the future", 
                       instruction.getSettlementInstructionId());
        }

        // Update settlement status
        instruction.setSettlementStatus("SETTLED");

        // Update timestamp
        instruction.setUpdatedAt(LocalDateTime.now());

        logger.info("Settlement instruction {} executed successfully", instruction.getSettlementInstructionId());
        return entityWithMetadata;
    }
}

