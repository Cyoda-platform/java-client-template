package com.java_template.application.processor;

import com.java_template.application.entity.Photo;
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
public class PhotoProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PhotoProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PhotoProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Photo for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Photo.class)
                .withErrorHandler(this::handlePhotoError)
                .validate(Photo::isValid, "Invalid Photo entity state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PhotoProcessor".equals(modelSpec.operationName()) &&
                "photo".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handlePhotoError(Throwable t, Photo photo) {
        logger.error("Error processing Photo entity", t);
        return new ErrorInfo("PhotoProcessingError", t.getMessage());
    }
}
