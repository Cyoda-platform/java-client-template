package com.java_template.application.processor;

import com.java_template.application.entity.risk_rule.version_1.RiskRule;
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
 * Processor for updating risk rules
 * Validates and updates risk control parameters
 */
@Component
public class RiskRuleUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RiskRuleUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RiskRuleUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RiskRuleUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(RiskRule.class)
                .validate(this::isValidEntityWithMetadata, "Invalid risk rule wrapper")
                .map(this::updateRule)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<RiskRule> entityWithMetadata) {
        RiskRule entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<RiskRule> updateRule(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<RiskRule> context) {

        EntityWithMetadata<RiskRule> entityWithMetadata = context.entityResponse();
        RiskRule rule = entityWithMetadata.entity();

        logger.debug("Updating risk rule: {} with limit: {}", rule.getRuleId(), rule.getLimitValue());

        // Validate limit value
        if (rule.getLimitValue() <= 0) {
            logger.error("Invalid limit value: {}", rule.getLimitValue());
            throw new IllegalArgumentException("Limit value must be positive");
        }

        // Validate threshold percentage
        if (rule.getThresholdPercent() != null && (rule.getThresholdPercent() < 0 || rule.getThresholdPercent() > 100)) {
            logger.error("Invalid threshold percentage: {}", rule.getThresholdPercent());
            throw new IllegalArgumentException("Threshold percentage must be between 0 and 100");
        }

        // Update timestamp
        rule.setUpdatedAt(LocalDateTime.now());

        logger.info("Risk rule {} updated successfully", rule.getRuleId());
        return entityWithMetadata;
    }
}

