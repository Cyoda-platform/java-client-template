package com.java_template.application.processor;

import com.java_template.application.entity.certificate.version_1.Certificate;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ABOUTME: CertificateProcessor handles certificate lifecycle operations including
 * issuance, renewal, and revocation with appropriate timestamp and state management.
 */
@Component
public class CertificateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CertificateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CertificateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Certificate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Certificate.class)
                .validate(this::isValidEntityWithMetadata, "Invalid certificate wrapper")
                .map(this::processCertificateLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Certificate> entityWithMetadata) {
        Certificate entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for certificate processing
     */
    private EntityWithMetadata<Certificate> processCertificateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Certificate> context) {

        EntityWithMetadata<Certificate> entityWithMetadata = context.entityResponse();
        Certificate certificate = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing certificate: {} in state: {}", certificate.getCertificateId(), currentState);

        // Handle different transitions
        if ("initial".equals(currentState)) {
            handleIssuance(certificate);
        } else if ("issued".equals(currentState)) {
            handleRenewalOrRevocation(certificate);
        }

        logger.info("Certificate {} processed successfully", certificate.getCertificateId());
        return entityWithMetadata;
    }

    /**
     * Handle certificate issuance
     */
    private void handleIssuance(Certificate certificate) {
        logger.debug("Issuing certificate: {}", certificate.getCertificateId());
        
        // Set issuance timestamp if not already set
        if (certificate.getIssuedDate() == null) {
            certificate.setIssuedDate(LocalDateTime.now());
        }
        
        // Ensure expiration date is set
        if (certificate.getExpirationDate() == null) {
            // Default to 1 year from issuance
            certificate.setExpirationDate(certificate.getIssuedDate().plusYears(1));
        }
    }

    /**
     * Handle certificate renewal or revocation
     */
    private void handleRenewalOrRevocation(Certificate certificate) {
        logger.debug("Processing renewal/revocation for certificate: {}", certificate.getCertificateId());
        
        // Check if this is a revocation (revokedDate is set)
        if (certificate.getRevokedDate() != null) {
            logger.info("Certificate {} has been revoked", certificate.getCertificateId());
        } else {
            // This is a renewal operation
            logger.info("Certificate {} has been renewed", certificate.getCertificateId());
            
            // Update expiration date for renewal
            if (certificate.getExpirationDate() != null) {
                certificate.setExpirationDate(certificate.getExpirationDate().plusYears(1));
            }
        }
    }
}

