package com.java_template.application.criteria;

import com.java_template.common.workflow.CriteriaChecker;
import com.java_template.common.workflow.ModelKey;
import com.java_template.application.entity.pet.Pet;
import com.java_template.application.serializer.CriteriaRequestSerializer;
import org.cyoda.cloud.api.event.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component("isvalidpet")
public class IsValidPet implements CriteriaChecker {

    private static final Logger logger = LoggerFactory.getLogger(IsValidPet.class);
    private final CriteriaRequestSerializer serializer;

    public IsValidPet(CriteriaRequestSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public CompletableFuture<EntityCriteriaCalculationResponse> check(EntityCriteriaCalculationRequest request) {
        logger.info("isValidPet criteria checker called");

        return CompletableFuture.supplyAsync(() -> {

                // Option 1: Type-safe extraction when we know it's a Pet
                Pet pet = serializer.extractEntity(request, Pet.class);
                boolean isValid = pet.isValid();
                logger.debug("Pet validation result: {}", isValid);
                return serializer.createSuccessResponse(request, isValid);

        });
    }

    @Override
    public boolean supports(ModelKey modelKey) {
        return "pet".equals(modelKey.modelName()) && ENTITY_VERSION.equals(modelKey.modelVersion());
    }

    @Override
    public String getName() {
        return "isvalidpet";
    }
}
