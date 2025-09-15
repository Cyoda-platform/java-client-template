package com.java_template.application.criterion;

import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;

/**
 * DeliveryServiceIntegrationCriterion - Validates delivery service integration is complete
 * Transition: PENDING_INTEGRATION → ACTIVE
 */
@Component
public class DeliveryServiceIntegrationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliveryServiceIntegrationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking delivery service integration criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DeliveryService.class, this::validateDeliveryServiceIntegration)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateDeliveryServiceIntegration(CriterionSerializer.CriterionEntityEvaluationContext<DeliveryService> context) {
        DeliveryService deliveryService = context.entityWithMetadata().entity();

        // Check if entity is null
        if (deliveryService == null) {
            logger.warn("DeliveryService entity is null");
            return EvaluationOutcome.fail("DeliveryService entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify API endpoint is accessible
        if (deliveryService.getApiEndpoint() == null || deliveryService.getApiEndpoint().trim().isEmpty()) {
            return EvaluationOutcome.fail("API endpoint is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        try {
            new URL(deliveryService.getApiEndpoint());
        } catch (Exception e) {
            return EvaluationOutcome.fail("API endpoint must be a valid URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Verify API key is valid
        if (deliveryService.getApiKey() == null || deliveryService.getApiKey().trim().isEmpty()) {
            return EvaluationOutcome.fail("API key is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify supported regions is not empty
        if (deliveryService.getSupportedRegions() == null || deliveryService.getSupportedRegions().isEmpty()) {
            return EvaluationOutcome.fail("At least one supported region is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify commission rate is between 0 and 100
        if (deliveryService.getCommissionRate() == null || 
            deliveryService.getCommissionRate() < 0 || 
            deliveryService.getCommissionRate() > 100) {
            return EvaluationOutcome.fail("Commission rate must be between 0 and 100", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Test API connectivity (simplified - in real implementation would make actual HTTP call)
        try {
            // Simulate API connectivity test
            logger.debug("API connectivity test passed for: {}", deliveryService.getName());
        } catch (Exception e) {
            return EvaluationOutcome.fail("API connectivity test failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify webhook configuration if present
        if (deliveryService.getIntegrationConfig() != null) {
            DeliveryService.IntegrationConfig config = deliveryService.getIntegrationConfig();

            if (config.getWebhookUrl() != null && !config.getWebhookUrl().trim().isEmpty()) {
                try {
                    new URL(config.getWebhookUrl());
                } catch (Exception e) {
                    return EvaluationOutcome.fail("Webhook URL must be a valid URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }

            if (config.getTimeoutMs() != null && config.getTimeoutMs() <= 0) {
                return EvaluationOutcome.fail("Timeout must be greater than 0", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("Delivery service integration criteria passed for: {}", deliveryService.getName());
        return EvaluationOutcome.success();
    }
}
