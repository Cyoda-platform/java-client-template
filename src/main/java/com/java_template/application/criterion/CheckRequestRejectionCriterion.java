package com.java_template.application.criterion;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CheckRequestRejectionCriterion implements CyodaCriterion {

    private final CriterionSerializer serializer;

    public CheckRequestRejectionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        log.info("Evaluating rejection criteria for adoption request: {}", request.getId());

        // For demo purposes, reject 20% of requests
        return serializer.withRequest(request)
            .evaluate(jsonNode -> {
                boolean shouldReject = Math.random() <= 0.2;
                if (shouldReject) {
                    log.info("Adoption request rejected");
                    return EvaluationOutcome.success(); // Success means the rejection criterion is met
                } else {
                    log.info("Adoption request not rejected by this criterion");
                    return EvaluationOutcome.fail("Request does not meet rejection criteria"); // Failure means the rejection criterion is not met
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return "CheckRequestRejectionCriterion".equals(opsSpec.operationName());
    }
}
