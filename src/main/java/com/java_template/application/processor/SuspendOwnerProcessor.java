package com.java_template.application.processor;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SuspendOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SuspendOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Owner.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.getAccountStatus() != null;
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        String currentStatus = entity.getAccountStatus();
        logger.info("SuspendOwnerProcessor invoked for owner id={} currentStatus={}", entity.getId(), currentStatus);

        // Idempotent: if already suspended, do nothing
        if (currentStatus != null && "suspended".equalsIgnoreCase(currentStatus)) {
            logger.info("Owner {} is already suspended. No action taken.", entity.getId());
            return entity;
        }

        // Only allow suspension from active state. If not active, log and leave unchanged.
        if (currentStatus == null || !"active".equalsIgnoreCase(currentStatus)) {
            logger.warn("Owner {} cannot be suspended from state '{}'. Expected state 'active'. No action taken.", entity.getId(), currentStatus);
            return entity;
        }

        // Perform suspension
        entity.setAccountStatus("suspended");
        logger.info("Owner {} accountStatus set to 'suspended'", entity.getId());

        return entity;
    }
}