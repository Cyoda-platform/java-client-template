package com.java_template.application.processor;

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
public class EmailDispatchProcessor implements CyodaProcessor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public EmailDispatchProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("EmailDispatchProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailDispatch.class)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchProcessor".equals(modelSpec.operationName()) &&
               "emaildispatch".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EmailDispatch processEntityLogic(EmailDispatch entity) {
        logger.info("Processing EmailDispatch with technicalId: {}", entity.getTechnicalId());

        logger.info("Sending email for Job ID {} in format {}", entity.getJobId(), entity.getEmailFormat());
        entity.setStatus(EmailDispatch.StatusEnum.SENT);

        CompletableFuture<Void> updateFuture = entityService.updateItem(
            "email_dispatch_model", Config.ENTITY_VERSION, entity.getTechnicalId(), entity)
            .thenAccept(updatedId -> logger.info("Email sent successfully for EmailDispatch technicalId: {}", entity.getTechnicalId()));

        updateFuture.join();

        return entity;
    }
}