package com.java_template.application.processor;

import com.java_template.application.entity.CompanySearchJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

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
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processCompanySearchJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CompanySearchJobProcessor".equals(modelSpec.operationName()) &&
                "companysearchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(CompanySearchJob entity) {
        if (entity.getCompanyName() == null || entity.getCompanyName().isBlank()) return false;
        if (entity.getOutputFormat() == null || entity.getOutputFormat().isBlank()) return false;
        String fmt = entity.getOutputFormat().toUpperCase();
        return fmt.equals("JSON") || fmt.equals("CSV");
    }

    private CompanySearchJob processCompanySearchJobLogic(CompanySearchJob entity) {
        logger.info("Executing business logic for CompanySearchJob with companyName: {}", entity.getCompanyName());

        // 1. Call PRH Avoindata YTJ API to search companies by companyName
        // For this example, simulate fetching companies

        List<Company> allCompanies = fetchCompaniesByName(entity.getCompanyName());

        // 2. Filter results to keep only active companies
        List<Company> activeCompanies = allCompanies.stream()
                .filter(c -> "Active".equalsIgnoreCase(c.getStatus()))
                .collect(Collectors.toList());

        // 3. For each active company, create LEIEnrichmentRequest entity with status PENDING
        List<LEIEnrichmentRequest> leiRequests = new ArrayList<>();
        for (Company company : activeCompanies) {
            LEIEnrichmentRequest leiReq = new LEIEnrichmentRequest();
            leiReq.setBusinessId(company.getBusinessId());
            leiReq.setStatus("PENDING");
            leiReq.setLeiSource(null);
            leiReq.setLei(null);
            try {
                entityService.addItem("LEIEnrichmentRequest", Config.ENTITY_VERSION, leiReq);
                leiRequests.add(leiReq);
            } catch (Exception e) {
                logger.error("Error adding LEIEnrichmentRequest for businessId: {}", company.getBusinessId(), e);
            }
        }

        // 4. Update CompanySearchJob status to PROCESSING
        entity.setStatus("PROCESSING");

        // 5. Trigger asynchronous processing of each LEI enrichment is handled externally

        return entity;
    }

    // Simulated method to fetch companies by name
    private List<Company> fetchCompaniesByName(String companyName) {
        logger.info("Simulating company search for name containing: {}", companyName);
        List<Company> companies = new ArrayList<>();

        Company exampleCompany = new Company();
        exampleCompany.setCompanyName(companyName);
        exampleCompany.setBusinessId("1234567-8");
        exampleCompany.setCompanyType("OY");
        exampleCompany.setRegistrationDate("2010-05-12");
        exampleCompany.setStatus("Active");
        exampleCompany.setLei(null);

        companies.add(exampleCompany);
        return companies;
    }
}
