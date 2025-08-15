package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class SubscriberValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");

    public SubscriberValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SubscriberValidationProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid subscriber payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber s) {
        return s != null && s.getName() != null && !s.getName().isBlank();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber s = context.entity();

        // Validate contactMethod and contactAddress
        String cm = s.getContactMethod();
        String addr = s.getContactAddress();
        boolean ok = true;

        if (cm == null) {
            logger.warn("Subscriber {} missing contactMethod", s.getTechnicalId());
            ok = false;
        } else {
            switch (cm) {
                case "EMAIL":
                    if (addr == null || !EMAIL_PATTERN.matcher(addr).matches()) {
                        logger.warn("Subscriber {} has invalid email address {}", s.getTechnicalId(), addr);
                        ok = false;
                    }
                    break;
                case "WEBHOOK":
                    if (addr == null || !URL_PATTERN.matcher(addr).matches()) {
                        logger.warn("Subscriber {} has invalid webhook URL {}", s.getTechnicalId(), addr);
                        ok = false;
                    }
                    break;
                case "SMS":
                    if (addr == null || addr.length() < 7) {
                        logger.warn("Subscriber {} has invalid SMS number {}", s.getTechnicalId(), addr);
                        ok = false;
                    }
                    break;
                default:
                    logger.warn("Subscriber {} has unknown contactMethod {}", s.getTechnicalId(), cm);
                    ok = false;
            }
        }

        if (!ok) {
            s.setActive(false);
            return s;
        }

        // subscriber passes validation
        s.setActive(true);
        logger.info("Subscriber {} validated and activated", s.getTechnicalId());
        return s;
    }
}
