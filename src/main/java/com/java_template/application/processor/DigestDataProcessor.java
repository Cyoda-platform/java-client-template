package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
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
public class DigestDataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestDataProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(DigestData.class)
                .validate(DigestData::isValid, "Invalid DigestData entity state")
                .map(this::processDigestData)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestDataProcessor".equals(modelSpec.operationName()) &&
                "digestdata".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestData processDigestData(DigestData entity) {
        // Business logic copied from processDigestData() flow in functional requirement

        // 1. Initial State: DigestData created with RETRIEVED status (already set before processing)

        // 2. Processing: Format or transform the raw data into the required digest format.
        // For demonstration, we simulate formatting by appending " - formatted" to data if data is not null.
        if (entity.getData() != null) {
            entity.setData(entity.getData() + " - formatted");
        }

        // 3. Trigger EmailDispatch creation with formatted data.
        //    This processor does not create EmailDispatch directly; assumed to be done downstream.

        // 4. Update DigestData status to PROCESSED.
        entity.setStatus(DigestData.StatusEnum.PROCESSED);

        return entity;
    }
}
