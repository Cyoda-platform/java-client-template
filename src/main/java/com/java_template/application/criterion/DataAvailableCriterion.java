package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.dataanalysis.version_1.DataAnalysis;
import com.java_template.application.entity.datasource.version_1.DataSource;
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
 * DataAvailableCriterion
 * Check if the referenced data source has completed download
 */
@Component
public class DataAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DataAvailableCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking DataAvailable criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DataAnalysis.class, this::validateEntity)
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
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataAnalysis> context) {
        DataAnalysis entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("DataAnalysis is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("DataAnalysis is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if dataSourceId is provided
        if (entity.getDataSourceId() == null || entity.getDataSourceId().trim().isEmpty()) {
            logger.warn("DataSourceId is null or empty");
            return EvaluationOutcome.fail("DataSourceId is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        try {
            // Find the data source by business ID
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.sourceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getDataSourceId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataSource>> dataSources = entityService.search(modelSpec, condition, DataSource.class);
            
            if (dataSources.isEmpty()) {
                logger.warn("DataSource not found for sourceId: {}", entity.getDataSourceId());
                return EvaluationOutcome.fail("DataSource not found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            EntityWithMetadata<DataSource> dataSourceWithMetadata = dataSources.get(0);
            DataSource dataSource = dataSourceWithMetadata.entity();
            String dataSourceState = dataSourceWithMetadata.metadata().getState();

            // Check if data source is in download_complete state
            if (!"download_complete".equals(dataSourceState)) {
                logger.warn("DataSource is not in download_complete state, current state: {}", dataSourceState);
                return EvaluationOutcome.fail("DataSource download not complete", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check if fileName is available
            if (dataSource.getFileName() == null || dataSource.getFileName().trim().isEmpty()) {
                logger.warn("DataSource fileName is null or empty");
                return EvaluationOutcome.fail("DataSource file not available", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            logger.debug("Data availability validation successful for dataSourceId: {}", entity.getDataSourceId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.warn("Data availability validation failed for dataSourceId: {}", entity.getDataSourceId(), e);
            return EvaluationOutcome.fail("Data availability check failed: " + e.getMessage(), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
