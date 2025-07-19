package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.DigestData;
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

import java.util.concurrent.CompletableFuture;

@Component
public class DigestRequestJobProcessor implements CyodaProcessor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public DigestRequestJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DigestRequestJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequestJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestRequestJob.class)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestJobProcessor".equals(modelSpec.operationName()) &&
               "digestrequestjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequestJob processEntityLogic(DigestRequestJob entity) {
        logger.info("Processing DigestRequestJob with technicalId: {}", entity.getTechnicalId());

        entity.setStatus(DigestRequestJob.StatusEnum.PROCESSING);

        CompletableFuture<Void> updateFuture = entityService.updateItem(
            "digest_request_job_model", Config.ENTITY_VERSION, entity.getTechnicalId(), entity)
            .thenCompose(updatedId -> {
                DigestData data = new DigestData();
                data.setJobId(entity.getTechnicalId().toString());
                data.setData("Sample data from petstore API based on metadata: " + (entity.getMetadata() != null ? entity.getMetadata().toString() : "{}"));
                data.setStatus(DigestData.StatusEnum.RETRIEVED);
                return entityService.addItem("digest_data_model", Config.ENTITY_VERSION, data)
                    .thenCompose(dataId -> {
                        data.setTechnicalId(dataId);
                        logger.info("Triggered DigestData creation with technicalId: {}", dataId);
                        // Note: We do not process DigestData entity here because it is handled separately
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenCompose(v -> {
                        entity.setStatus(DigestRequestJob.StatusEnum.COMPLETED);
                        return entityService.updateItem("digest_request_job_model", Config.ENTITY_VERSION, entity.getTechnicalId(), entity)
                                .thenAccept(updated2 -> logger.info("DigestRequestJob marked COMPLETED with technicalId: {}", entity.getTechnicalId()));
                    });
            });

        // Wait for async chain to complete synchronously because processor expects synchronous return
        updateFuture.join();

        return entity;
    }
}