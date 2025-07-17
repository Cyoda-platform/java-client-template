package com.java_template.application.processor;

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
            .validate(this::isValidEntity, "Invalid DigestData entity state")
            .map(this::processDigestData)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestDataProcessor".equals(modelSpec.operationName()) &&
               "digestData".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Validation method
    private boolean isValidEntity(DigestData digestData) {
        return digestData.isValid();
    }

    // Business processing logic
    private DigestData processDigestData(DigestData digestData) {
        // Example logic: Set processedAt timestamp if not set
        if (digestData.getProcessedAt() == null) {
            digestData.setProcessedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            logger.info("DigestData processedAt timestamp set");
        }
        return digestData;
    }
}
