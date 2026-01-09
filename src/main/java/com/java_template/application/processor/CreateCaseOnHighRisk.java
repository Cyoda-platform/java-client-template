package com.java_template.application.processor;

import com.example.application.entity.customer.version_1.Customer;
import com.example.application.entity.case_entity.version_1.Case;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CreateCaseOnHighRisk Processor - KYC Onboarding Workflow
 * 
 * Creates a compliance case for high-risk customers identified during KYC.
 * Initiates manual review process and escalation procedures.
 */
@Component
public class CreateCaseOnHighRisk implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateCaseOnHighRisk.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateCaseOnHighRisk(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Creating case for high-risk customer for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Customer> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.debug("Creating case for high-risk customer: {}", customer.getId());

        // Check if risk score is high
        if (customer.getMetadata() != null) {
            Object riskScoreObj = customer.getMetadata().get("riskScore");
            if (riskScoreObj instanceof Number) {
                double riskScore = ((Number) riskScoreObj).doubleValue();
                if (riskScore >= 70) {
                    createComplianceCase(customer);
                }
            }
        }

        return entityWithMetadata;
    }

    private void createComplianceCase(Customer customer) {
        try {
            Case complianceCase = new Case();
            complianceCase.setId("CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            complianceCase.setTitle("High-Risk KYC Review - " + customer.getId());
            complianceCase.setDescription("Compliance case created for high-risk customer during KYC onboarding. " +
                    "Customer: " + customer.getLegalName() + ", Type: " + customer.getCustomerType());
            complianceCase.setCreatedBy("system");
            complianceCase.setAssignees(new ArrayList<>());
            complianceCase.setStatus(Case.CaseStatus.OPEN);
            complianceCase.setAlerts(new ArrayList<>());
            complianceCase.setEvidences(new ArrayList<>());
            complianceCase.setCreatedAt(LocalDateTime.now());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("customerId", customer.getId());
            metadata.put("customerName", customer.getLegalName());
            metadata.put("customerType", customer.getCustomerType());
            metadata.put("riskScore", customer.getMetadata().get("riskScore"));
            metadata.put("riskLevel", customer.getMetadata().get("riskLevel"));
            metadata.put("caseType", "KYC_HIGH_RISK");
            metadata.put("priority", "HIGH");
            complianceCase.setMetadata(metadata);

            entityService.create(complianceCase);
            logger.info("Compliance case created: {} for customer: {}", complianceCase.getId(), customer.getId());

            // Update customer metadata with case reference
            if (customer.getMetadata() == null) {
                customer.setMetadata(new HashMap<>());
            }
            customer.getMetadata().put("relatedCaseId", complianceCase.getId());
        } catch (Exception e) {
            logger.error("Failed to create compliance case for customer: {}", customer.getId(), e);
        }
    }
}

