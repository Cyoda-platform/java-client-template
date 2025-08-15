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
import java.util.HashMap;
import java.util.Map;

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
        // Use entity.isValid() as authoritative validation
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail entity = context.entity();

        try {
            // Ignore any client-provided isHappy - determine via criteria
            boolean happy = false;
            try {
                happy = isHappyCriterion.evaluate(entity);
                logger.debug("IsHappyCriterion returned {} for mail {}", happy, entity.getTechnicalId());
            } catch (Exception e) {
                logger.warn("IsHappyCriterion evaluation threw exception for mail {}. Treating as not happy.", entity.getTechnicalId(), e);
                happy = false;
            }

            if (happy) {
                entity.setIsHappy(Boolean.TRUE);
                logger.info("Mail {} classified as HAPPY", entity.getTechnicalId());
            } else {
                boolean gloomy = false;
                try {
                    gloomy = isGloomyCriterion.evaluate(entity);
                    logger.debug("IsGloomyCriterion returned {} for mail {}", gloomy, entity.getTechnicalId());
                } catch (Exception e) {
                    logger.warn("IsGloomyCriterion evaluation threw exception for mail {}. Treating as not gloomy.", entity.getTechnicalId(), e);
                    gloomy = false;
                }
                // If gloomy true -> not happy, else default to not happy per requirements
                entity.setIsHappy(Boolean.FALSE);
                logger.info("Mail {} classified as GLOOMY={} (happy={})", entity.getTechnicalId(), gloomy, happy);
            }

            // Ensure deliveryStatus structure exists with canonical fields
            try {
                if (entity.getDeliveryStatus() == null) {
                    Map<String, Object> ds = new HashMap<>();
                    ds.put("attempts", 0);
                    ds.put("status", "PENDING");
                    ds.put("lastAttempt", null);
                    ds.put("lastError", null);
                    ds.put("perRecipient", null);
                    entity.setDeliveryStatus(ds);
                }
            } catch (Exception e) {
                // Some entities might not have deliveryStatus accessors; log and continue
                logger.debug("Could not initialize deliveryStatus for mail {}: {}", entity == null ? "<null>" : entity.getTechnicalId(), e.getMessage());
            }

            // Transition to EVALUATED and set updatedAt
            try {
                entity.setState("EVALUATED");
            } catch (Exception t) {
                logger.debug("Entity does not support setState or state is managed externally: {}", t.getMessage());
            }
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception t) {
                logger.debug("Entity does not support setUpdatedAt: {}", t.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error while evaluating criteria for mail {}", entity == null ? "<null>" : entity.getTechnicalId(), e);
        }

        return entity;
    }
}
