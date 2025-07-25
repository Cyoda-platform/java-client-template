package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetStoreApiUrlInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetStoreApiUrlInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetStoreApiUrlInvalidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        // Validate petStoreApiUrl is null or blank to mark invalid
        String petStoreApiUrl = request.getEntity().get("petStoreApiUrl");
        if (petStoreApiUrl == null || petStoreApiUrl.isBlank()) {
            return serializer.withRequest(request)
                .success()
                .complete();
        }
        // Basic URL format check (simple regex)
        if (!petStoreApiUrl.matches("https?://[\w\-\.]+(:\d+)?(/\S*)?")) {
            return serializer.withRequest(request)
                .success()
                .complete();
        }

        return serializer.withRequest(request)
            .fail("petStoreApiUrl is valid", StandardEvalReasonCategories.VALIDATION_FAILURE)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetStoreApiUrlInvalidCriterion".equals(modelSpec.operationName()) &&
               "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
