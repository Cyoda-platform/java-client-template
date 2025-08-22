package com.java_template.application.processor;

import com.java_template.application.entity.address.version_1.Address;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddressArchivedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddressArchivedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddressArchivedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AddressArchivedProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Address.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Address entity) {
        return entity != null && entity.isValid();
    }

    private Address processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Address> context) {
        Address entity = context.entity();
        try {
            if (entity == null) {
                logger.debug("Address entity is null, nothing to process.");
                return entity;
            }

            String status = entity.getStatus();
            // Only archive addresses that are Verified
            if (status == null || !"Verified".equalsIgnoreCase(status.trim())) {
                logger.debug("Address {} not in Verified state (status={}), skipping archival.", entity.getId(), status);
                return entity;
            }

            // Never archive default addresses
            if (Boolean.TRUE.equals(entity.getIsDefault())) {
                logger.debug("Address {} is marked as default, skipping archival.", entity.getId());
                return entity;
            }

            // Business rule: Archive the address if it is verified and not default.
            // Note: In a full implementation we would verify that no other entities (Users/Orders)
            // reference this address before archiving. Per constraints we avoid external updates here.
            logger.info("Archiving Address {} as it is verified and not default.", entity.getId());
            entity.setStatus("Archived");
            // Ensure isDefault flag is unset
            entity.setIsDefault(false);
        } catch (Exception ex) {
            logger.warn("Unexpected error during AddressArchivedProcessor for address {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
        }
        return entity;
    }
}