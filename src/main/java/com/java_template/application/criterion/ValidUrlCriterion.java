package com.java_template.application.criterion;

import com.java_template.application.entity.datasource.version_1.DataSource;
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

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ValidUrlCriterion
 * Validates that the URL is accessible and returns CSV data
 */
@Component
public class ValidUrlCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidUrlCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidUrl criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DataSource.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataSource> context) {
        DataSource entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("DataSource is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("DataSource is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate URL accessibility
        if (entity.getUrl() == null || entity.getUrl().trim().isEmpty()) {
            logger.warn("URL is null or empty");
            return EvaluationOutcome.fail("URL is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        try {
            URL url = new URL(entity.getUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.warn("URL is not accessible, response code: {}", responseCode);
                return EvaluationOutcome.fail("URL is not accessible (HTTP " + responseCode + ")", 
                    StandardEvalReasonCategories.EXTERNAL_DEPENDENCY_FAILURE);
            }

            String contentType = connection.getContentType();
            if (contentType != null && !contentType.toLowerCase().contains("text")) {
                logger.warn("URL does not return text content, content type: {}", contentType);
                return EvaluationOutcome.fail("URL does not return text content", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            logger.debug("URL validation successful for: {}", entity.getUrl());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.warn("URL validation failed for: {}", entity.getUrl(), e);
            return EvaluationOutcome.fail("URL validation failed: " + e.getMessage(), 
                StandardEvalReasonCategories.EXTERNAL_DEPENDENCY_FAILURE);
        }
    }
}
