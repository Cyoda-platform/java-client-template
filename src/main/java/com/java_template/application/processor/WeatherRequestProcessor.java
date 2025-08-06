package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.WeatherRequest;
import com.java_template.application.entity.WeatherData;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class WeatherRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public WeatherRequestProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeatherRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(WeatherRequest.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeatherRequest entity) {
        return entity != null && entity.isValid();
    }

    private WeatherRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeatherRequest> context) {
        WeatherRequest entity = context.entity();
        String technicalId = context.request().getEntityId();

        logger.info("Starting processWeatherRequest for technicalId: {}", technicalId);

        // Basic validation to ensure either cityName or lat/long present
        if ((entity.getCityName() == null || entity.getCityName().isBlank()) &&
                (entity.getLatitude() == null || entity.getLongitude() == null)) {
            logger.error("WeatherRequest {} missing location information", technicalId);
            return entity;
        }

        // Simulated weather data points for current or forecast
        List<WeatherData> fetchedData = new ArrayList<>();

        if ("CURRENT".equalsIgnoreCase(entity.getRequestType()) || "FORECAST".equalsIgnoreCase(entity.getRequestType())) {
            WeatherData data = new WeatherData();
            data.setWeatherRequestId(technicalId);
            data.setDataType(entity.getRequestType().toUpperCase());
            data.setTemperature(20.0 + Math.random() * 10); // random temp between 20-30
            data.setHumidity(50.0 + Math.random() * 50); // random humidity 50-100%
            data.setWindSpeed(1.0 + Math.random() * 10); // random wind speed
            data.setPrecipitation(Math.random() * 5); // random precip
            data.setObservationTime(Instant.now());

            if (!data.isValid()) {
                logger.error("Generated WeatherData invalid for request {}", technicalId);
                return entity;
            }

            fetchedData.add(data);
        } else {
            logger.error("Unsupported requestType {} for WeatherRequest {}", entity.getRequestType(), technicalId);
            return entity;
        }

        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    WeatherData.ENTITY_NAME,
                    "1",
                    fetchedData
            );
            List<UUID> ids = idsFuture.get();
            for (UUID id : ids) {
                logger.info("Stored WeatherData with technicalId: {}", id.toString());
            }
        } catch (Exception e) {
            logger.error("Error storing WeatherData for WeatherRequest {}: {}", technicalId, e.getMessage());
        }

        logger.info("Completed processWeatherRequest for technicalId: {}", technicalId);

        return entity;
    }

}