package com.java_template.application.processor;

import com.java_template.application.criterion.IsGloomyCriterion;
import com.java_template.application.criterion.IsHappyCriterion;
import com.java_template.application.entity.mail.version_1.Mail;
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

import java.time.Instant;

@Component
public class EvaluateCriteriaProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EvaluateCriteriaProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final IsHappyCriterion isHappyCriterion;
    private final IsGloomyCriterion isGloomyCriterion;

    public EvaluateCriteriaProcessor(SerializerFactory serializerFactory,
                                     IsHappyCriterion isHappyCriterion,
                                     IsGloomyCriterion isGloomyCriterion) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.isHappyCriterion = isHappyCriterion;
        this.isGloomyCriterion = isGloomyCriterion;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail evaluation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.getMailList() != null && !entity.getMailList().isEmpty();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail entity = context.entity();

        // Always ignore client provided isHappy; determine via criteria
        try {
            boolean happy = false;
            // Evaluate happy first
            try {
                happy = isHappyCriterion.evaluate(entity);
            } catch (Exception e) {
                logger.warn("IsHappyCriterion evaluation threw exception, treating as not happy", e);
                happy = false;
            }

            if (happy) {
                entity.setIsHappy(Boolean.TRUE);
                logger.info("Mail {} classified as HAPPY", entity.getTechnicalId());
            } else {
                boolean gloomy = false;
                try {
                    gloomy = isGloomyCriterion.evaluate(entity);
                } catch (Exception e) {
                    logger.warn("IsGloomyCriterion evaluation threw exception, treating as not gloomy", e);
                    gloomy = false;
                }
                // default to false if neither applies
                entity.setIsHappy(gloomy ? Boolean.FALSE : Boolean.FALSE);
                logger.info("Mail {} classified as GLOOMY={}", entity.getTechnicalId(), !happy);
            }

            entity.setState("EVALUATED");
            entity.setUpdatedAt(Instant.now().toString());

        } catch (Exception e) {
            logger.error("Error while evaluating criteria for mail {}", entity == null ? "<null>" : entity.getTechnicalId(), e);
        }

        return entity;
    }
}
