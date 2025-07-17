package com.java_template.application.criteria;

import com.java_template.application.entity.DigestRequest;
import com.java_template.common.workflow.CyodaCriteria;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.springframework.stereotype.Component;

@Component
public class DigestRequestCriteria implements CyodaCriteria {

    @Override
    public boolean evaluate(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        DigestRequest entity = context.getEvent().getEntity(DigestRequest.class);
        return entity != null && entity.getEmail() != null && !entity.getEmail().isEmpty();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestCriteria".equals(modelSpec.operationName()) &&
                "digestRequest".equals(modelSpec.modelKey().getName());
    }
}
