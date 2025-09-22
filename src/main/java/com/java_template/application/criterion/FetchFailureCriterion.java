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
 * FetchFailureCriterion
 * 
 * Checks if data fetch operation failed.
 * Used in DataSource workflow transition: fetch_failed
 */
@Component
public class FetchFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking fetch failure criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DataSource.class, this::validateFetchFailure)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic to check if fetch operation failed
     */
    private EvaluationOutcome validateFetchFailure(CriterionSerializer.CriterionEntityEvaluationContext<DataSource> context) {
        DataSource dataSource = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (dataSource == null) {
            logger.warn("DataSource is null");
            return EvaluationOutcome.fail("DataSource entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if fetch failed - lastFetchTime should be null if fetch failed
        if (dataSource.getLastFetchTime() == null) {
            logger.warn("Data fetch failed for DataSource: {}", dataSource.getDataSourceId());
            return EvaluationOutcome.fail("Data fetch operation failed - no fetch timestamp",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if file size is null or zero, which might indicate fetch failure
        if (dataSource.getFileSize() == null || dataSource.getFileSize() <= 0) {
            logger.warn("Data fetch failed for DataSource: {} - invalid file size", dataSource.getDataSourceId());
            return EvaluationOutcome.fail("Data fetch operation failed - invalid file size", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if checksum is missing, which might indicate fetch failure
        if (dataSource.getChecksum() == null || dataSource.getChecksum().trim().isEmpty()) {
            logger.warn("Data fetch failed for DataSource: {} - missing checksum", dataSource.getDataSourceId());
            return EvaluationOutcome.fail("Data fetch operation failed - missing checksum", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // If we reach here, fetch was successful
        logger.debug("Data fetch was successful for DataSource: {}", dataSource.getDataSourceId());
        return EvaluationOutcome.success();
    }
}
