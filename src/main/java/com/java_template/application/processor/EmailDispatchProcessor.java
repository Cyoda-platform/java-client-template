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

import java.time.Instant;

@Component
public class EmailDispatchProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public EmailDispatchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("EmailDispatchProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailDispatch.class)
            .validate(EmailDispatch::isValid, "Invalid EmailDispatch entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchProcessor".equals(modelSpec.operationName()) &&
               "emailDispatch".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EmailDispatch processEntityLogic(EmailDispatch ed) {
        logger.info("Simulating email send to {}", ed.getEmail());
        try {
            Thread.sleep(500);
            ed.setStatus(EmailDispatch.Status.SENT);
            ed.setSentAt(Instant.now());
            logger.info("Email sent to {}", ed.getEmail());
        } catch (InterruptedException e) {
            logger.error("Email send interrupted", e);
            ed.setStatus(EmailDispatch.Status.FAILED);
            ed.setErrorMessage("Interrupted");
        }
        return ed;
    }
}
