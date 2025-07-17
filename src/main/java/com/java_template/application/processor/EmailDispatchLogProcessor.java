package com.java_template.application.processor;

import com.java_template.application.entity.EmailDispatchLog;
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

@Component
public class EmailDispatchLogProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public EmailDispatchLogProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("EmailDispatchLogProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatchLog for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(EmailDispatchLog.class)
                .validate(this::isValidEntity, "Invalid EmailDispatchLog entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchLogProcessor".equals(modelSpec.operationName()) &&
                "emailDispatchLog".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(EmailDispatchLog entity) {
        return entity.isValid();
    }

    private EmailDispatchLog processEntityLogic(EmailDispatchLog entity) {
        // Example logic: if dispatchStatus is PENDING, update timestamp to now
        if (entity.getDispatchStatus() == EmailDispatchLog.DispatchStatus.PENDING) {
            entity.setSentAt(new java.sql.Timestamp(System.currentTimeMillis()));
        }
        return entity;
    }
}
