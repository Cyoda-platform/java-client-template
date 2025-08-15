package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.lead.version_1.Lead;
import com.java_template.application.entity.opportunity.version_1.Opportunity;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CreateOpportunityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOpportunityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOpportunityProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Lead for opportunity creation, request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Lead.class)
            .validate(this::isValidEntity, "Invalid lead entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Lead lead) {
        return lead != null;
    }

    private Lead processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Lead> context) {
        Lead lead = context.entity();
        String technicalId = null;
        try {
            try {
                technicalId = context.request().getEntityId();
            } catch (Exception ignored) {}

            // Build Opportunity from lead
            Opportunity opp = new Opportunity();
            if (lead.getFirstName() != null || lead.getLastName() != null) {
                StringBuilder name = new StringBuilder();
                if (lead.getFirstName() != null) name.append(lead.getFirstName());
                if (lead.getLastName() != null) {
                    if (name.length() > 0) name.append(' ');
                    name.append(lead.getLastName());
                }
                if (name.length() > 0) opp.setName(name.toString());
            }
            if (lead.getCompany() != null) opp.setContactId(null); // will be linked if contact exists
            if (lead.getCompany() != null) opp.setName(opp.getName() == null ? lead.getCompany() : opp.getName());
            // use potential value mapping if present on lead - lead may not have numeric potentialValue in this model, so skip

            // Link the lead technical id
            if (technicalId != null) {
                opp.setLeadId(technicalId);
            }

            // Provide defaults
            if (opp.getStage() == null) opp.setStage("PROSPECTING");

            // Persist opportunity
            try {
                UUID oppId = entityService.addItem(
                    Opportunity.ENTITY_NAME,
                    String.valueOf(Opportunity.ENTITY_VERSION),
                    opp
                ).join();
                logger.info("Created Opportunity {} from Lead {}", oppId, technicalId);
            } catch (Exception ex) {
                logger.error("Failed to persist Opportunity created from lead {}: {}", technicalId, ex.getMessage());
            }

            // Update lead status
            try {
                lead.setStatus("OPPORTUNITY_CREATED");
                if (technicalId != null) {
                    entityService.updateItem(
                        Lead.ENTITY_NAME,
                        String.valueOf(Lead.ENTITY_VERSION),
                        UUID.fromString(technicalId),
                        lead
                    ).join();
                }
            } catch (Exception ex) {
                logger.error("Failed to update Lead {} status after opportunity creation: {}", technicalId, ex.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while creating opportunity from lead {}: {}", technicalId, e.getMessage());
        }
        return lead;
    }
}
