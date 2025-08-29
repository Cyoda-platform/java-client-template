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
public class CheckRequestApprovalCriterion implements CyodaCriterion {

    private final CriterionSerializer serializer;

    public CheckRequestApprovalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        log.info("Evaluating approval criteria for adoption request: {}", request.getId());

        // For demo purposes, approve 80% of requests
        return serializer.withRequest(request)
            .evaluate(jsonNode -> {
                boolean isApproved = Math.random() > 0.2;
                if (isApproved) {
                    log.info("Adoption request approved");
                    return EvaluationOutcome.success();
                } else {
                    log.info("Adoption request not approved by this criterion");
                    return EvaluationOutcome.fail("Request does not meet approval criteria");
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return "CheckRequestApprovalCriterion".equals(opsSpec.operationName());
    }
}
