package com.java_template.application.criteria;

import com.java_template.application.entity.PetUpdateJob;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;

public class IsJobPendingCriterion implements CyodaCriterion {

    @Override
    public boolean test(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        PetUpdateJob job = context.getEvent().getEntity(PetUpdateJob.class);
        return job != null && "PENDING".equalsIgnoreCase(job.getStatus());
    }
}
