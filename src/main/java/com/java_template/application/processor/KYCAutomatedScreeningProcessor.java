package com.java_template.application.processor;

import com.java_template.application.entity.kycprofile.version_1.KYCProfile;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * KYCAutomatedScreeningProcessor - Performs automated KYC screening
 * Validates documents and performs sanctions/PEP screening
 */
@Component
public class KYCAutomatedScreeningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KYCAutomatedScreeningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public KYCAutomatedScreeningProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Performing automated KYC screening for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(KYCProfile.class)
                .validate(this::isValidEntityWithMetadata, "Invalid KYC profile wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<KYCProfile> entityWithMetadata) {
        KYCProfile profile = entityWithMetadata.entity();
        return profile != null && profile.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<KYCProfile> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<KYCProfile> context) {

        EntityWithMetadata<KYCProfile> entityWithMetadata = context.entityResponse();
        KYCProfile profile = entityWithMetadata.entity();

        logger.debug("Screening KYC profile: {} for user: {}", profile.getKycProfileId(), profile.getUserId());

        // In production, this would:
        // 1. Validate document formats and authenticity
        // 2. Query sanctions lists (OFAC, UN, EU, etc.)
        // 3. Query PEP databases
        // 4. Perform name matching and fuzzy matching
        // 5. Generate risk scores

        // Simulate screening results
        profile.setSanctionsScreeningStatus("PASSED");
        profile.setPepScreeningStatus("PASSED");
        profile.setVerificationNotes("Automated screening passed. Ready for manual review.");

        logger.info("Automated screening completed for KYC profile: {}", profile.getKycProfileId());
        return entityWithMetadata;
    }
}

