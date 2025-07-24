package com.java_template.application.processor;

import com.java_template.application.entity.CompanySearchJob;
import com.java_template.application.entity.Company;
import com.java_template.application.entity.LEIEnrichmentRequest;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;

@Component
public class CompanySearchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CompanySearchJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("CompanySearchJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CompanySearchJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(CompanySearchJob.class)
                .validate(CompanySearchJob::isValid, "Invalid CompanySearchJob entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CompanySearchJobProcessor".equals(modelSpec.operationName()) &&
                "companysearchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private CompanySearchJob processEntityLogic(CompanySearchJob job) {
        String id = job.getModelKey().getModelSpec().getTechnicalId();
        if (id == null) {
            logger.error("Missing technical ID for CompanySearchJob entity");
            return job;
        }

        try {
            if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
                logger.error("CompanySearchJob ID {} has invalid companyName", id);
                job.setStatus("FAILED");
                // No update method available, so just set status
                return job;
            }

            String outputFormat = job.getOutputFormat().toUpperCase(Locale.ROOT);
            if (!outputFormat.equals("JSON") && !outputFormat.equals("CSV")) {
                logger.error("CompanySearchJob ID {} has invalid outputFormat: {}", id, outputFormat);
                job.setStatus("FAILED");
                return job;
            }

            job.setStatus("PROCESSING");

            // Call PRH API to search companies by name
            List<Company> retrievedCompanies = searchCompaniesByName(job.getCompanyName());

            // Filter active companies only
            List<Company> activeCompanies = new ArrayList<>();
            for (Company c : retrievedCompanies) {
                if ("Active".equalsIgnoreCase(c.getStatus())) {
                    activeCompanies.add(c);
                }
            }

            for (Company company : activeCompanies) {
                LEIEnrichmentRequest leiRequest = new LEIEnrichmentRequest();
                leiRequest.setBusinessId(company.getBusinessId());
                leiRequest.setLeiSource(null);
                leiRequest.setStatus("PENDING");
                leiRequest.setLei("Not Available");

                UUID leiRequestTechnicalId = entityService.addItem("LEIEnrichmentRequest", Config.ENTITY_VERSION, leiRequest).get();
                String leiRequestId = leiRequestTechnicalId.toString();

                // Asynchronous processing is assumed here; simulate immediate processing
                processLEIEnrichmentRequest(leiRequestId, leiRequest);

                ObjectNode enrichmentNode = entityService.getItem("LEIEnrichmentRequest", Config.ENTITY_VERSION, leiRequestTechnicalId).get();
                if (enrichmentNode != null && enrichmentNode.hasNonNull("lei")) {
                    company.setLei(enrichmentNode.get("lei").asText());
                }

                UUID companyTechnicalId = entityService.addItem("Company", Config.ENTITY_VERSION, company).get();
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now().toString());

            logger.info("Completed processing CompanySearchJob with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error processing CompanySearchJob with ID: {}", id, e);
            job.setStatus("FAILED");
        }

        return job;
    }

    private List<Company> searchCompaniesByName(String companyName) {
        logger.info("Simulating PRH API call for company name: {}", companyName);

        List<Company> companies = new ArrayList<>();

        if (companyName.toLowerCase(Locale.ROOT).contains("example")) {
            Company c1 = new Company();
            c1.setCompanyName("Example Oy");
            c1.setBusinessId("1234567-8");
            c1.setCompanyType("OY");
            c1.setRegistrationDate("2010-05-12");
            c1.setStatus("Active");
            c1.setLei("Not Available");
            companies.add(c1);

            Company c2 = new Company();
            c2.setCompanyName("Example Inactive Oy");
            c2.setBusinessId("9999999-9");
            c2.setCompanyType("OY");
            c2.setRegistrationDate("2005-03-15");
            c2.setStatus("Inactive");
            c2.setLei("Not Available");
            companies.add(c2);
        }

        return companies;
    }

    private void processLEIEnrichmentRequest(String id, LEIEnrichmentRequest request) {
        // Simulated processing logic for LEI enrichment request
        logger.info("Processing LEIEnrichmentRequest with ID: {}", id);

        try {
            // Simulate calling external LEI API and updating status accordingly
            request.setLei("Simulated-LEI-1234567890");
            request.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error processing LEIEnrichmentRequest with ID: {}", id, e);
            request.setStatus("FAILED");
        }
    }
}
