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
 * Processor for confirming settlement instructions
 * Validates settlement details before execution
 */
@Component
public class SettlementConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SettlementConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SettlementConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SettlementConfirmation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SettlementInstruction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid settlement instruction wrapper")
                .map(this::confirmInstruction)
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

    private EntityWithMetadata<SettlementInstruction> confirmInstruction(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SettlementInstruction> context) {

        EntityWithMetadata<SettlementInstruction> entityWithMetadata = context.entityResponse();
        SettlementInstruction instruction = entityWithMetadata.entity();

        logger.debug("Confirming settlement instruction: {}", instruction.getSettlementInstructionId());

        // Validate settlement amount
        Double expectedAmount = instruction.getQuantity() * instruction.getSettlementPrice();
        if (Math.abs(instruction.getSettlementAmount() - expectedAmount) > 0.01) {
            logger.warn("Settlement amount mismatch: expected={}, actual={}", 
                       expectedAmount, instruction.getSettlementAmount());
        }

        // Update settlement status
        instruction.setSettlementStatus("CONFIRMED");

        // Update timestamp
        instruction.setUpdatedAt(LocalDateTime.now());

        logger.info("Settlement instruction {} confirmed successfully", instruction.getSettlementInstructionId());
        return entityWithMetadata;
    }
}

