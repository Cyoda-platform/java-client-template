package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
import com.java_template.common.workflow.Criterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PetJobTypeIsUpdatePetStatusCriterion implements Criterion {

    private static final Logger logger = LoggerFactory.getLogger(PetJobTypeIsUpdatePetStatusCriterion.class);

    @Override
    public boolean test(Object entity) {
        if (entity instanceof PetJob) {
            PetJob petJob = (PetJob) entity;
            boolean result = "UpdatePetStatus".equalsIgnoreCase(petJob.getType());
            logger.info("PetJobTypeIsUpdatePetStatusCriterion test result: {} for PetJob type: {}", result, petJob.getType());
            return result;
        }
        logger.warn("PetJobTypeIsUpdatePetStatusCriterion received non-PetJob entity");
        return false;
    }
}
