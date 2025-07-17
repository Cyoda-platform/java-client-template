package com.java_template.application.processor;

import com.java_template.application.entity.Match;
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

import java.util.function.BiFunction;

@Component
public class MatchProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public MatchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("MatchProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Match for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Match.class)
                .withErrorHandler(this::handleMatchError)
                .validate(this::isValidMatch, "Invalid Match entity state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MatchProcessor".equals(modelSpec.operationName()) &&
               "match".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidMatch(Match match) {
        return match.isValid();
    }

    private ErrorInfo handleMatchError(Throwable t, Match match) {
        logger.error("Error processing Match entity", t);
        return new ErrorInfo("MATCH_PROCESSOR_ERROR", t.getMessage());
    }
}
