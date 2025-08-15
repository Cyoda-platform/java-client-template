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
public class EnrichContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EnrichContactProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Contact enrichment for request: {}", request.getId());

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

            // Enrichment logic: basic company enrichment and scoring
            String company = contact.getCompany();
            if (company == null || company.isEmpty()) {
                // attempt to enrich company from email domain
                String email = contact.getEmail();
                if (email != null && email.contains("@")) {
                    String domain = email.substring(email.indexOf("@") + 1);
                    contact.setCompany(domain);
                }
            }
            // Use title field to record enrichment status since Contact entity does not have a status field
            if (contact.getTitle() == null || contact.getTitle().isEmpty()) {
                contact.setTitle("ENRICHED");
            }

            // Persist enriched contact state
            try {
                if (technicalId != null) {
                    entityService.updateItem(Contact.ENTITY_NAME, String.valueOf(Contact.ENTITY_VERSION), UUID.fromString(technicalId), contact).join();
                }
            } catch (Exception ex) {
                logger.warn("Failed to persist enriched contact {}: {}", technicalId, ex.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error enriching contact {}: {}", contact.getEmail(), e.getMessage());
        }
        return contact;
    }
}
