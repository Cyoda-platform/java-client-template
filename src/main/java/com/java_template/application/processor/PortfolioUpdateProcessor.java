package com.java_template.application.processor;

import com.java_template.application.entity.portfolio.version_1.Portfolio;
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
 * Processor for updating portfolio data
 * Handles cash balance updates and position list changes
 */
@Component
public class PortfolioUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PortfolioUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PortfolioUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Portfolio.class)
                .validate(this::isValidEntityWithMetadata, "Invalid portfolio wrapper")
                .map(this::updatePortfolio)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Portfolio> entityWithMetadata) {
        Portfolio entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Portfolio> updatePortfolio(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Portfolio> context) {

        EntityWithMetadata<Portfolio> entityWithMetadata = context.entityResponse();
        Portfolio portfolio = entityWithMetadata.entity();

        logger.debug("Updating portfolio: {} with cash balance: {}", 
                   portfolio.getPortfolioId(), portfolio.getCashBalance());

        // Validate cash balance
        if (portfolio.getCashBalance() < 0) {
            logger.warn("Negative cash balance for portfolio: {}", portfolio.getPortfolioId());
        }

        // Update timestamp
        portfolio.setUpdatedAt(LocalDateTime.now());

        logger.info("Portfolio {} updated successfully", portfolio.getPortfolioId());
        return entityWithMetadata;
    }
}

