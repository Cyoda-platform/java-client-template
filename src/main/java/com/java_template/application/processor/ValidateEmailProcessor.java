package com.java_template.application.processor;

import com.java_template.application.entity.recipient.version_1.Recipient;
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

import java.util.regex.Pattern;

@Component
public class ValidateEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    public ValidateEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Recipient for email validation request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Recipient.class)
            .validate(this::isValidEntity, "Invalid Recipient entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Recipient entity) {
        return entity != null && entity.getEmail() != null;
    }

    private Recipient processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Recipient> context) {
        Recipient recipient = context.entity();
        try {
            String email = recipient.getEmail();
            if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
                recipient.setStatus("INVALID");
            } else {
                recipient.setStatus("VERIFIED");
            }
            recipient.setUpdatedAt(java.time.OffsetDateTime.now().toString());
        } catch (Exception ex) {
            logger.error("Error validating recipient email", ex);
        }
        return recipient;
    }
}
