package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
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
public class DigestDataProcessor implements CyodaProcessor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public DigestDataProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DigestDataProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestData.class)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestDataProcessor".equals(modelSpec.operationName()) &&
               "digestdata".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestData processEntityLogic(DigestData entity) {
        logger.info("Processing DigestData with technicalId: {}", entity.getTechnicalId());
        // Format or transform raw data into digest format (e.g. HTML)
        String formattedData = "<html><body><h1>Digest Data</h1><p>" + entity.getData() + "</p></body></html>";
        entity.setData(formattedData);
        entity.setStatus(DigestData.StatusEnum.PROCESSED);

        CompletableFuture<Void> updateFuture = entityService.updateItem(
            "digest_data_model", Config.ENTITY_VERSION, entity.getTechnicalId(), entity)
            .thenCompose(updatedId -> {
                EmailDispatch dispatch = new EmailDispatch();
                dispatch.setJobId(entity.getJobId());
                dispatch.setEmailFormat(EmailDispatch.EmailFormatEnum.HTML);
                dispatch.setStatus(EmailDispatch.StatusEnum.QUEUED);
                return entityService.addItem("email_dispatch_model", Config.ENTITY_VERSION, dispatch)
                    .thenCompose(dispatchId -> {
                        dispatch.setTechnicalId(dispatchId);
                        logger.info("Triggered EmailDispatch creation with technicalId: {}", dispatchId);
                        // Note: We do not process EmailDispatch entity here because it is handled separately
                        return CompletableFuture.completedFuture(null);
                    });
            });

        // Wait for async chain to complete synchronously because processor expects synchronous return
        updateFuture.join();

        return entity;
    }
}