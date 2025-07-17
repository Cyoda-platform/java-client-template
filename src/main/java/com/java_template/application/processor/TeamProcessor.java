package com.java_template.application.processor;

import com.java_template.application.entity.Team;
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
public class TeamProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public TeamProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("TeamProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Team for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Team.class)
                .withErrorHandler(this::handleTeamError)
                .validate(this::isValidTeam, "Invalid Team entity state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "TeamProcessor".equals(modelSpec.operationName()) &&
               "team".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidTeam(Team team) {
        return team.isValid();
    }

    private ErrorInfo handleTeamError(Throwable t, Team team) {
        logger.error("Error processing Team entity", t);
        return new ErrorInfo("TEAM_PROCESSOR_ERROR", t.getMessage());
    }
}
