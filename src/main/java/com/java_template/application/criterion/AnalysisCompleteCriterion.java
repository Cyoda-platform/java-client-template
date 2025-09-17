package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.dataanalysis.version_1.DataAnalysis;
import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AnalysisCompleteCriterion
 * Check if the referenced analysis has completed successfully
 */
@Component
public class AnalysisCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalysisCompleteCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AnalysisComplete criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailNotification.class, this::validateEntity)
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
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailNotification> context) {
        EmailNotification entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("EmailNotification is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("EmailNotification is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if analysisId is provided
        if (entity.getAnalysisId() == null || entity.getAnalysisId().trim().isEmpty()) {
            logger.warn("AnalysisId is null or empty");
            return EvaluationOutcome.fail("AnalysisId is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        try {
            // Find the analysis by business ID
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.analysisId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getAnalysisId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataAnalysis>> analyses = entityService.search(modelSpec, condition, DataAnalysis.class);
            
            if (analyses.isEmpty()) {
                logger.warn("DataAnalysis not found for analysisId: {}", entity.getAnalysisId());
                return EvaluationOutcome.fail("DataAnalysis not found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            EntityWithMetadata<DataAnalysis> analysisWithMetadata = analyses.get(0);
            DataAnalysis analysis = analysisWithMetadata.entity();
            String analysisState = analysisWithMetadata.metadata().getState();

            // Check if analysis is in analysis_complete state
            if (!"analysis_complete".equals(analysisState)) {
                logger.warn("DataAnalysis is not in analysis_complete state, current state: {}", analysisState);
                return EvaluationOutcome.fail("DataAnalysis not complete", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check if reportData is available
            if (analysis.getReportData() == null || analysis.getReportData().trim().isEmpty()) {
                logger.warn("DataAnalysis reportData is null or empty");
                return EvaluationOutcome.fail("DataAnalysis report not available", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            logger.debug("Analysis completion validation successful for analysisId: {}", entity.getAnalysisId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.warn("Analysis completion validation failed for analysisId: {}", entity.getAnalysisId(), e);
            return EvaluationOutcome.fail("Analysis completion check failed: " + e.getMessage(), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
