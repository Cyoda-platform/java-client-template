package com.java_template.application.processor;

import com.java_template.application.entity.DigestDataRecord;
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

@Component
public class DigestDataRecordProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DigestDataRecordProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestDataRecordProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestDataRecord for request: {}", request.getId());

        // Business logic from processDigestDataRecord() flow in functional_requirement.md
        return serializer.withRequest(request)
                .toEntity(DigestDataRecord.class)
                .validate(this::isValidEntity, "Invalid DigestDataRecord state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(DigestDataRecord entity) {
        return entity != null && entity.isValid();
    }

    private DigestDataRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DigestDataRecord> context) {
        DigestDataRecord entity = context.entity();

        // processDigestDataRecord flow logic:
        // 1. Initial State: DigestDataRecord created after API data fetch
        // 2. Validation: Verify responseData integrity (optional)
        // 3. Persistence: Store fetched data for aggregation
        // 4. Completion: Mark fetch as completed (could be implicit by creation)

        // Since this processor is for DigestDataRecord and the entity is already
        // validated and persisted, we do not need to modify the entity here.

        return entity;
    }
}
