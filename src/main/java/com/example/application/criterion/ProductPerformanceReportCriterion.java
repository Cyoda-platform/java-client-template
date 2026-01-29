package com.example.application.criterion;

import com.example.application.entity.product_performance_report.version_1.ProductPerformanceReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.CriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.CriterionCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProductPerformanceReportCriterion
 * Criterion for evaluating workflow transitions based on report state
 */
@Component
public class ProductPerformanceReportCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(ProductPerformanceReportCriterion.class);
    private final String className = this.getClass().getSimpleName();

    @Override
    public CriterionCalculationResponse evaluate(CyodaEventContext<CriterionCalculationRequest> context) {
        CriterionCalculationRequest request = context.getEvent();
        logger.debug("Evaluating criterion for request: {}", request.getId());

        try {
            // Extract entity data from request
            Object entityData = request.getEntity();
            
            if (entityData == null) {
                logger.warn("No entity data provided for criterion evaluation");
                return CriterionCalculationResponse.builder()
                        .requestId(request.getId())
                        .result(false)
                        .build();
            }

            // Evaluate based on entity state
            boolean result = evaluateCriterion(entityData);

            logger.debug("Criterion evaluation result: {}", result);
            return CriterionCalculationResponse.builder()
                    .requestId(request.getId())
                    .result(result)
                    .build();
        } catch (Exception e) {
            logger.error("Error evaluating criterion", e);
            return CriterionCalculationResponse.builder()
                    .requestId(request.getId())
                    .result(false)
                    .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Evaluates the criterion based on report state
     * Returns true if the report is ready for the next transition
     */
    private boolean evaluateCriterion(Object entityData) {
        // In a real scenario, you would deserialize and check specific conditions
        // For now, we return true to allow transitions
        logger.debug("Evaluating report readiness for transition");
        return true;
    }
}

