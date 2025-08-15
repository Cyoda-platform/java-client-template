package com.java_template.application.processor;

import com.java_template.application.entity.contact.version_1.Contact;
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
public class EnrichContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        // Enrichment logic: basic company enrichment and scoring
        try {
            String company = contact.getCompany();
            if (company == null || company.isEmpty()) {
                // attempt to enrich company from email domain
                String email = contact.getEmail();
                if (email != null && email.contains("@")) {
                    String domain = email.substring(email.indexOf("@") + 1);
                    contact.setCompany(domain);
                }
            }
            // set status to ENRICHED if not already
            if (contact.getStatus() == null || contact.getStatus().isEmpty() || "PENDING".equals(contact.getStatus())) {
                contact.setStatus("ENRICHED");
            }
        } catch (Exception e) {
            logger.error("Error enriching contact {}: {}", contact.getTechnicalId(), e.getMessage());
        }
        return contact;
    }
}
