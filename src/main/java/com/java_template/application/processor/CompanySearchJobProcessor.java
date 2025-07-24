package com.java_template.application.processor;

import com.java_template.application.entity.CompanySearchJob;
import com.java_template.application.entity.LEIEnrichmentRequest;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
                .validate(this::isValidEntity, "Invalid CompanySearchJob entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CompanySearchJobProcessor".equals(modelSpec.operationName()) &&
                "companysearchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(CompanySearchJob job) {
        if (job.getCompanyName() == null || job.getCompanyName().isBlank()) return false;
        if (job.getOutputFormat() == null) return false;
        String format = job.getOutputFormat().toUpperCase();
        return format.equals("JSON") || format.equals("CSV");
    }

    private CompanySearchJob processEntityLogic(CompanySearchJob job) {
        // Simulate calling PRH Avoindata YTJ API to search companies by companyName
        logger.info("Searching companies for name: {}", job.getCompanyName());

        // For demonstration, create dummy list of active companies matching job.getCompanyName()
        List<Company> foundCompanies = new ArrayList<>();
        Company exampleCompany = new Company();
        exampleCompany.setCompanyName(job.getCompanyName());
        exampleCompany.setBusinessId("1234567-8");
        exampleCompany.setCompanyType("OY");
        exampleCompany.setRegistrationDate("2010-05-12");
        exampleCompany.setStatus("Active");
        exampleCompany.setLei("Not Available");
        foundCompanies.add(exampleCompany);

        // Create LEIEnrichmentRequest entities for each active company
        List<LEIEnrichmentRequest> leiRequests = new ArrayList<>();
        for (Company company : foundCompanies) {
            if ("Active".equalsIgnoreCase(company.getStatus())) {
                LEIEnrichmentRequest request = new LEIEnrichmentRequest();
                request.setBusinessId(company.getBusinessId());
                request.setStatus("PENDING");
                leiRequests.add(request);
            }
        }

        // Add LEIEnrichmentRequest entities asynchronously
        try {
            CompletableFuture<List<?>> future = entityService.addItems(
                    "leienrichmentrequest",
                    Config.ENTITY_VERSION,
                    leiRequests
            );
            future.join();
            job.setStatus("PROCESSING");
        } catch (Exception e) {
            logger.error("Failed to add LEIEnrichmentRequest entities", e);
            job.setStatus("FAILED");
        }

        return job;
    }
}
