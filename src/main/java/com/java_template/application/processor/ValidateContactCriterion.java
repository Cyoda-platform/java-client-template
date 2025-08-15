package com.java_template.application.processor;

import com.java_template.application.entity.contact.version_1.Contact;
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

import java.util.UUID;

@Component
public class ValidateContactCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateContactCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateContactCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateContactCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Contact.class)
            .validate(this::isValidEntity, "Invalid contact")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Contact contact) {
        return contact != null;
    }

    private Contact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Contact> context) {
        Contact contact = context.entity();
        String technicalId = null;
        try {
            try { technicalId = context.request().getEntityId(); } catch (Exception ignored) {}

            boolean missing = false;
            if (contact.getFirstName() == null || contact.getFirstName().isEmpty()) missing = true;
            if (contact.getLastName() == null || contact.getLastName().isEmpty()) missing = true;
            String email = contact.getEmail();
            if (email == null || email.isEmpty() || !email.contains("@")) missing = true;

            if (missing) {
                contact.setStatus("DELETED");
            } else {
                contact.setStatus("VALIDATED");
            }

            // persist
            if (technicalId != null) {
                entityService.updateItem(Contact.ENTITY_NAME, String.valueOf(Contact.ENTITY_VERSION), UUID.fromString(technicalId), contact).join();
            }
        } catch (Exception e) {
            logger.error("Error validating contact {}: {}", technicalId, e.getMessage());
        }
        return contact;
    }
}
