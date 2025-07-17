package com.java_template.application.processor;

import com.java_template.application.entity.DigestEmail;
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
public class DigestEmailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestEmailProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestEmail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestEmail.class)
            .validate(de -> de.getDigestRequestId() != null && !de.getDigestRequestId().isEmpty(), "Invalid DigestEmail: digestRequestId is required")
            .validate(de -> de.getContent() != null && !de.getContent().isEmpty(), "Invalid DigestEmail: content is required")
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestEmailProcessor".equals(modelSpec.operationName()) &&
               "digestEmail".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
