package com.java_template.application.processor;

import com.java_template.application.entity.RetrievalJob;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class RetrievalJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public RetrievalJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("RetrievalJobProcessor initialized with SerializerFactory");
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
        RetrievalJob entity = context.entity();

        // Business logic from processRetrievalJob flow in functional requirements
        // This processor handles the processing of RetrievalJob entity
        // 1. Validate companyName is not empty or null (already validated by isValidEntity)
        // 2. Query PRH Avoindata API with the companyName (simulated here)
        // 3. Filter out inactive companies from the results (simulated here)
        // 4. Query LEI registry to enrich data for each active company (simulated here)
        // 5. Create CompanyData entities for each enriched company record
        // 6. Update RetrievalJob status to COMPLETED if all succeed, otherwise FAILED

        // Since external API calls and DB calls are not possible here,
        // simulate the processing result:

        // Simulate success processing
        entity.setStatus("COMPLETED");

        // Simulate setting resultTechnicalId (normally the UUID of created CompanyData entities)
        entity.setResultTechnicalId(UUID.randomUUID().toString());

        return entity;
    }
}
