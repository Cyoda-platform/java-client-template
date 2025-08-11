package com.java_template.application.processor;

import com.java_template.application.entity.Laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class LaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        if (laureate == null) {
            return false;
        }
        if (laureate.getLaureateId() == null) {
            return false;
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().trim().isEmpty()) {
            return false;
        }
        if (laureate.getSurname() == null || laureate.getSurname().trim().isEmpty()) {
            return false;
        }
        if (laureate.getYear() == null || laureate.getYear().trim().isEmpty()) {
            return false;
        }
        if (laureate.getCategory() == null || laureate.getCategory().trim().isEmpty()) {
            return false;
        }
        return true;
    }

    private Laureate processEntityLogic(Laureate laureate) {
        // Enrichment: calculate ageAtAward if born date is present
        if (laureate.getBorn() != null && !laureate.getBorn().trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
                LocalDate bornDate = LocalDate.parse(laureate.getBorn(), formatter);
                if (laureate.getYear() != null) {
                    int awardYear = Integer.parseInt(laureate.getYear());
                    int ageAtAward = awardYear - bornDate.getYear();
                    laureate.setAgeAtAward(ageAtAward);
                }
            } catch (DateTimeParseException | NumberFormatException e) {
                logger.warn("Failed to parse dates for laureateId {}: {}", laureate.getLaureateId(), e.getMessage());
                laureate.setAgeAtAward(null);
            }
        } else {
            laureate.setAgeAtAward(null);
        }
        return laureate;
    }
}
