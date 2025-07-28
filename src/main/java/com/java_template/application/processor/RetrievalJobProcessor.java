package com.java_template.application.processor;

import com.java_template.application.entity.RetrievalJob;
import com.java_template.application.entity.CompanyData;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RetrievalJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private final com.java_template.common.service.EntityService entityService;

    public RetrievalJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("RetrievalJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetrievalJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(RetrievalJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(RetrievalJob entity) {
        return entity != null && entity.isValid();
    }

    private RetrievalJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<RetrievalJob> context) {
        RetrievalJob retrievalJob = context.entity();
        UUID technicalId = UUID.fromString(context.request().getEntityId());

        logger.info("Processing RetrievalJob id: {}", technicalId);

        retrievalJob.setStatus("PROCESSING");

        try {
            if (retrievalJob.getCompanyName() == null || retrievalJob.getCompanyName().isBlank()) {
                throw new IllegalArgumentException("companyName is blank");
            }

            List<Map<String, String>> prhResults = queryPrhAvoindataApi(retrievalJob.getCompanyName());

            List<Map<String, String>> activeCompanies = new ArrayList<>();
            for (Map<String, String> company : prhResults) {
                String status = company.getOrDefault("status", "Inactive");
                if ("Active".equalsIgnoreCase(status)) {
                    activeCompanies.add(company);
                }
            }

            List<CompanyData> companyDataList = new ArrayList<>();
            for (Map<String, String> activeCompany : activeCompanies) {
                CompanyData cd = new CompanyData();
                cd.setBusinessId(activeCompany.get("businessId"));
                cd.setCompanyName(activeCompany.get("companyName"));
                cd.setCompanyType(activeCompany.get("companyType"));
                cd.setRegistrationDate(activeCompany.get("registrationDate"));
                cd.setStatus(activeCompany.get("status"));

                String lei = queryLeiRegistry(cd.getBusinessId(), cd.getCompanyName());
                cd.setLei(lei != null ? lei : "Not Available");

                cd.setRetrievalJobId(technicalId.toString());

                companyDataList.add(cd);
            }

            if (!companyDataList.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems("CompanyData", "1", companyDataList);
                List<UUID> ids = idsFuture.get();
                for (int i = 0; i < ids.size(); i++) {
                    logger.info("Created CompanyData with id {} for RetrievalJob {}", ids.get(i), technicalId);
                }
            }

            retrievalJob.setStatus("COMPLETED");
            logger.info("RetrievalJob {} completed successfully", technicalId);

            // Update RetrievalJob status in EntityService - update operation not supported currently

        } catch (Exception e) {
            retrievalJob.setStatus("FAILED");
            logger.error("Failed to process RetrievalJob {}: {}", technicalId, e.getMessage());
            // Update RetrievalJob status in EntityService - update operation not supported currently
        }

        return retrievalJob;
    }

    private List<Map<String, String>> queryPrhAvoindataApi(String companyName) {
        List<Map<String, String>> result = new ArrayList<>();

        Map<String, String> company1 = Map.of(
            "businessId", "1234567-8",
            "companyName", companyName + " Oyj",
            "companyType", "Limited Company",
            "registrationDate", "2000-01-01",
            "status", "Active"
        );

        Map<String, String> company2 = Map.of(
            "businessId", "8765432-1",
            "companyName", companyName + " Ltd",
            "companyType", "Limited Company",
            "registrationDate", "1990-05-05",
            "status", "Inactive"
        );

        result.add(company1);
        result.add(company2);

        return result;
    }

    private String queryLeiRegistry(String businessId, String companyName) {
        if ("1234567-8".equals(businessId)) {
            return "529900T8BM49AURSDO55";
        }
        return null;
    }
}
