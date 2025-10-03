package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment_file.version_1.PaymentFile;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Interactor for payment file business logic. Handles all payment file-related operations
 * including CRUD and basic management functionality.
 */
@Component
public class PaymentFileInteractor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentFileInteractor.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentFileInteractor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public EntityWithMetadata<PaymentFile> createPaymentFile(PaymentFile paymentFile) {
        // Validate business key is mandatory
        if (paymentFile.getPaymentFileId() == null || paymentFile.getPaymentFileId().trim().isEmpty()) {
            logger.error("PaymentFile creation failed: paymentFileId is mandatory");
            throw new IllegalArgumentException("paymentFileId is mandatory and cannot be null or empty");
        }

        // Check for duplicate business key
        ModelSpec modelSpec = new ModelSpec().withName(PaymentFile.ENTITY_NAME).withVersion(PaymentFile.ENTITY_VERSION);
        EntityWithMetadata<PaymentFile> existing = entityService.findByBusinessId(
                modelSpec, paymentFile.getPaymentFileId(), "paymentFileId", PaymentFile.class);

        if (existing != null) {
            logger.warn("PaymentFile with paymentFileId {} already exists", paymentFile.getPaymentFileId());
            throw new DuplicateEntityException("PaymentFile with paymentFileId '" + paymentFile.getPaymentFileId() + "' already exists");
        }

        paymentFile.setReceivedAt(LocalDateTime.now());
        paymentFile.setCreatedAt(LocalDateTime.now());
        paymentFile.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<PaymentFile> response = entityService.create(paymentFile);
        logger.info("PaymentFile created with ID: {}", response.metadata().getId());
        return response;
    }

    public EntityWithMetadata<PaymentFile> getPaymentFileById(UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName(PaymentFile.ENTITY_NAME).withVersion(PaymentFile.ENTITY_VERSION);
        EntityWithMetadata<PaymentFile> response = entityService.getById(id, modelSpec, PaymentFile.class);
        logger.debug("Retrieved payment file by ID: {}", id);
        return response;
    }

    public EntityWithMetadata<PaymentFile> getPaymentFileByBusinessId(String paymentFileId) {
        ModelSpec modelSpec = new ModelSpec().withName(PaymentFile.ENTITY_NAME).withVersion(PaymentFile.ENTITY_VERSION);
        EntityWithMetadata<PaymentFile> response = entityService.findByBusinessId(
                modelSpec, paymentFileId, "paymentFileId", PaymentFile.class);

        if (response == null) {
            logger.warn("PaymentFile not found with business ID: {}", paymentFileId);
            throw new EntityNotFoundException("PaymentFile not found with paymentFileId: " + paymentFileId);
        }
        
        logger.debug("Retrieved payment file by business ID: {}", paymentFileId);
        return response;
    }

    public EntityWithMetadata<PaymentFile> updatePaymentFileById(UUID id, PaymentFile paymentFile, String transition) {
        paymentFile.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<PaymentFile> response = entityService.update(id, paymentFile, transition);
        logger.info("PaymentFile updated with ID: {}", id);
        return response;
    }

    public EntityWithMetadata<PaymentFile> updatePaymentFileByBusinessId(String paymentFileId, PaymentFile paymentFile, String transition) {
        paymentFile.setUpdatedAt(LocalDateTime.now());

        EntityWithMetadata<PaymentFile> response = entityService.updateByBusinessId(paymentFile, "paymentFileId", transition);
        logger.info("PaymentFile updated with business ID: {}", paymentFileId);
        return response;
    }

    public List<EntityWithMetadata<PaymentFile>> getAllPaymentFiles() {
        ModelSpec modelSpec = new ModelSpec().withName(PaymentFile.ENTITY_NAME).withVersion(PaymentFile.ENTITY_VERSION);
        List<EntityWithMetadata<PaymentFile>> paymentFiles = entityService.findAll(modelSpec, PaymentFile.class);
        logger.debug("Retrieved {} payment files", paymentFiles.size());
        return paymentFiles;
    }

    /**
     * Exception thrown when attempting to create a duplicate entity
     */
    public static class DuplicateEntityException extends RuntimeException {
        public DuplicateEntityException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an entity is not found
     */
    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }
}

