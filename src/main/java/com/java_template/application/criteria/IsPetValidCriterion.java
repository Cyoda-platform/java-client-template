package com.java_template.application.criteria;

import com.java_template.application.entity.Pet;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;

public class IsPetValidCriterion implements CyodaCriterion {

    @Override
    public boolean test(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        Pet pet = context.getEvent().getEntity(Pet.class);
        if (pet == null) {
            return false;
        }
        return pet.getName() != null && !pet.getName().isBlank()
                && pet.getStatus() != null && !pet.getStatus().isBlank();
    }
}
