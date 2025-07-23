package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
import com.java_template.common.workflow.CriterionFunction;
import com.java_template.common.workflow.Criterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PetJobTypeIsAddPetCriterion implements Criterion {

    private static final Logger logger = LoggerFactory.getLogger(PetJobTypeIsAddPetCriterion.class);

    @Override
    public boolean test(Object entity) {
        if (entity instanceof PetJob) {
            PetJob petJob = (PetJob) entity;
            boolean result = "AddPet".equalsIgnoreCase(petJob.getType());
            logger.info("PetJobTypeIsAddPetCriterion test result: {} for PetJob type: {}", result, petJob.getType());
            return result;
        }
        logger.warn("PetJobTypeIsAddPetCriterion received non-PetJob entity");
        return false;
    }
}
