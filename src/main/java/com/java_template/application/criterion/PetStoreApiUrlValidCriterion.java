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
public class PetStoreApiUrlValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetStoreApiUrlValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetStoreApiUrlValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        // Validate petStoreApiUrl is not null and is a valid URL format
        String petStoreApiUrl = request.getEntity().get("petStoreApiUrl");
        if (petStoreApiUrl == null || petStoreApiUrl.isBlank()) {
            return serializer.withRequest(request)
                .fail("petStoreApiUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE)
                .complete();
        }
        // Basic URL format check (simple regex)
        if (!petStoreApiUrl.matches("https?://[\w\-\.]+(:\d+)?(/\S*)?")) {
            return serializer.withRequest(request)
                .fail("petStoreApiUrl is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE)
                .complete();
        }

        return serializer.withRequest(request)
            .success()
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetStoreApiUrlValidCriterion".equals(modelSpec.operationName()) &&
               "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
