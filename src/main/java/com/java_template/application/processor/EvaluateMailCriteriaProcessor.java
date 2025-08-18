package com.java_template.application.processor;

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

import java.util.List;

@Component
public class EvaluateMailCriteriaProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EvaluateMailCriteriaProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EvaluateMailCriteriaProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail evaluation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid Mail entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail mail) {
        if (mail == null) return false;
        List<String> list = mail.getMailList();
        return list != null && !list.isEmpty();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        // Business logic:
        // If isHappy explicitly provided by the client, honor it. Otherwise, deterministically evaluate
        // based on recipient addresses: if any recipient contains the substring "happy" (case-insensitive),
        // mark as happy. This is deterministic and simple for this prototype.

        Boolean provided = mail.getIsHappy();
        if (provided == null) {
            boolean happy = mail.getMailList().stream()
                .filter(e -> e != null)
                .anyMatch(e -> e.toLowerCase().contains("happy"));
            mail.setIsHappy(happy);
            logger.debug("IsHappy not provided; evaluated to {} for mail {}", happy, mail.getTechnicalId());
        } else {
            logger.debug("IsHappy provided by client as {} for mail {}", provided, mail.getTechnicalId());
        }

        // After evaluation, transition to READY_TO_SEND
        mail.setStatus("READY_TO_SEND");
        return mail;
    }
}
