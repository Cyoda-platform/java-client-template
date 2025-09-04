package com.java_template.application.criterion;

import com.java_template.application.entity.report.version_1.Report;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

@Component
public class ReportNoFiltersCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper;

    public ReportNoFiltersCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report entity = context.entity();
        
        try {
            // Check if filterCriteria field is null or empty
            if (entity.getFilterCriteria() == null || entity.getFilterCriteria().trim().isEmpty()) {
                logger.info("No filters specified for report {}, proceeding directly to calculation", entity.getReportId());
                return EvaluationOutcome.success();
            }
            
            // Parse the filter criteria to check if it's effectively empty
            Map<String, Object> filters = objectMapper.readValue(entity.getFilterCriteria(), new TypeReference<Map<String, Object>>() {});
            
            if (filters == null || filters.isEmpty()) {
                logger.info("Empty filter criteria for report {}, proceeding directly to calculation", entity.getReportId());
                return EvaluationOutcome.success();
            }
            
            // Check if all filter values are null or empty
            boolean hasValidFilters = filters.values().stream()
                .anyMatch(value -> value != null && !value.toString().trim().isEmpty());
            
            if (!hasValidFilters) {
                logger.info("All filter values are empty for report {}, proceeding directly to calculation", entity.getReportId());
                return EvaluationOutcome.success();
            }
            
            // If we reach here, there are actual filters present
            logger.info("Filters detected for report {}, proceeding with filtering step", entity.getReportId());
            return EvaluationOutcome.fail("Filters are present, filtering step required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            
        } catch (Exception e) {
            logger.error("Error parsing filter criteria for report {}: {}", entity.getReportId(), e.getMessage(), e);
            // If we can't parse the filters, assume they exist and require filtering
            return EvaluationOutcome.fail("Error parsing filter criteria, filtering step required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
    }
}
