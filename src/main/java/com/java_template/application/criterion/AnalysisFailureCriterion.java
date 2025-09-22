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

/**
 * AnalysisFailureCriterion
 * 
 * Checks if data analysis operation failed.
 * Used in DataSource workflow transition: analysis_failed
 */
@Component
public class AnalysisFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AnalysisFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking analysis failure criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DataSource.class, this::validateAnalysisFailure)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic to check if analysis operation failed
     */
    private EvaluationOutcome validateAnalysisFailure(CriterionSerializer.CriterionEntityEvaluationContext<DataSource> context) {
        DataSource dataSource = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (dataSource == null) {
            logger.warn("DataSource is null");
            return EvaluationOutcome.fail("DataSource entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if analysis failed - lastAnalysisTime should be null if analysis failed
        if (dataSource.getLastAnalysisTime() == null) {
            logger.warn("Data analysis failed for DataSource: {}", dataSource.getDataSourceId());
            return EvaluationOutcome.fail("Data analysis operation failed - no analysis timestamp", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if record count is null or negative, which might indicate analysis failure
        if (dataSource.getRecordCount() == null || dataSource.getRecordCount() < 0) {
            logger.warn("Data analysis failed for DataSource: {} - invalid record count", dataSource.getDataSourceId());
            return EvaluationOutcome.fail("Data analysis operation failed - invalid record count", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if analysis timestamp is before fetch timestamp (invalid state)
        if (dataSource.getLastFetchTime() != null && 
            dataSource.getLastAnalysisTime().isBefore(dataSource.getLastFetchTime())) {
            logger.warn("Data analysis failed for DataSource: {} - analysis timestamp before fetch timestamp", 
                       dataSource.getDataSourceId());
            return EvaluationOutcome.fail("Data analysis operation failed - invalid timestamp sequence", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // If we reach here, analysis was successful
        logger.debug("Data analysis was successful for DataSource: {}", dataSource.getDataSourceId());
        return EvaluationOutcome.success();
    }
}
