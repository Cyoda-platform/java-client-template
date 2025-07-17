package com.java_template.application.processor;

import com.java_template.application.entity.RetrievedData;
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
public class RetrievedDataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public RetrievedDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("RetrievedDataProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetrievedData for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(RetrievedData.class)
            .validate(rd -> rd.getDigestRequestId() != null && !rd.getDigestRequestId().isEmpty(), "Invalid RetrievedData: digestRequestId is required")
            .validate(rd -> rd.getDataPayload() != null, "Invalid RetrievedData: dataPayload is required")
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "RetrievedDataProcessor".equals(modelSpec.operationName()) &&
               "retrievedData".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
