package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class FetchBookingsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchBookingsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    public FetchBookingsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract ReportJob: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        // Mark job as fetching
        try {
            entity.setStatus("FETCHING");
        } catch (Exception e) {
            logger.warn("Unable to set status to FETCHING: {}", e.getMessage());
        }

        // Prepare filters
        ReportJob.Filters filters = entity.getFilters();

        LocalDate filterFrom = null;
        LocalDate filterTo = null;
        Integer minPrice = null;
        Integer maxPrice = null;
        Boolean depositPaidFilter = null;
        try {
            if (filters != null) {
                if (filters.getDateFrom() != null && !filters.getDateFrom().isBlank()) {
                    filterFrom = LocalDate.parse(filters.getDateFrom(), dateFormatter);
                }
                if (filters.getDateTo() != null && !filters.getDateTo().isBlank()) {
                    filterTo = LocalDate.parse(filters.getDateTo(), dateFormatter);
                }
                minPrice = filters.getMinPrice();
                maxPrice = filters.getMaxPrice();
                depositPaidFilter = filters.getDepositPaid();
            }
        } catch (Exception e) {
            logger.error("Invalid filter date format: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
            return entity;
        }

        List<Booking> persisted = new ArrayList<>();

        try {
            // 1) Fetch list of booking ids from Restful Booker
            HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://restful-booker.herokuapp.com/booking"))
                .GET()
                .build();

            HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());
            if (listResponse.statusCode() != 200) {
                logger.error("Failed to fetch booking list. Status: {}", listResponse.statusCode());
                entity.setStatus("FAILED");
                return entity;
            }

            JsonNode idArray = objectMapper.readTree(listResponse.body());
            if (idArray == null || !idArray.isArray()) {
                logger.warn("No bookings returned from external API.");
            } else {
                for (JsonNode idNode : idArray) {
                    if (idNode == null || idNode.get("bookingid") == null) continue;
                    int bookingId = idNode.get("bookingid").asInt();

                    // Fetch details
                    HttpRequest detailRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://restful-booker.herokuapp.com/booking/" + bookingId))
                        .GET()
                        .build();

                    HttpResponse<String> detailResponse = httpClient.send(detailRequest, HttpResponse.BodyHandlers.ofString());
                    if (detailResponse.statusCode() != 200) {
                        logger.warn("Failed to fetch booking details for id {}. Status: {}", bookingId, detailResponse.statusCode());
                        continue;
                    }

                    JsonNode detailNode = objectMapper.readTree(detailResponse.body());
                    if (detailNode == null || detailNode.isNull()) continue;

                    Booking booking = new Booking();
                    booking.setBookingId(bookingId);
                    if (detailNode.hasNonNull("firstname")) booking.setFirstname(detailNode.get("firstname").asText());
                    if (detailNode.hasNonNull("lastname")) booking.setLastname(detailNode.get("lastname").asText());
                    if (detailNode.has("bookingdates") && detailNode.get("bookingdates").hasNonNull("checkin"))
                        booking.setCheckin(detailNode.get("bookingdates").get("checkin").asText());
                    if (detailNode.has("bookingdates") && detailNode.get("bookingdates").hasNonNull("checkout"))
                        booking.setCheckout(detailNode.get("bookingdates").get("checkout").asText());
                    if (detailNode.hasNonNull("depositpaid")) booking.setDepositpaid(detailNode.get("depositpaid").asBoolean());
                    if (detailNode.hasNonNull("totalprice")) {
                        // totalprice can be int or double
                        if (detailNode.get("totalprice").isDouble() || detailNode.get("totalprice").isFloatingPointNumber()) {
                            booking.setTotalprice(detailNode.get("totalprice").asDouble());
                        } else {
                            booking.setTotalprice((double) detailNode.get("totalprice").asLong());
                        }
                    }
                    if (detailNode.hasNonNull("additionalneeds")) booking.setAdditionalneeds(detailNode.get("additionalneeds").asText());

                    booking.setSource("RestfulBooker");
                    booking.setPersistedAt(Instant.now().toString());

                    // Apply filters if present
                    boolean include = true;

                    try {
                        if (filterFrom != null || filterTo != null) {
                            String checkinStr = booking.getCheckin();
                            if (checkinStr == null || checkinStr.isBlank()) {
                                include = false;
                            } else {
                                LocalDate checkinDate = LocalDate.parse(checkinStr, dateFormatter);
                                if (filterFrom != null && checkinDate.isBefore(filterFrom)) include = false;
                                if (filterTo != null && checkinDate.isAfter(filterTo)) include = false;
                            }
                        }

                        if (include && minPrice != null) {
                            Double tp = booking.getTotalprice();
                            if (tp == null || tp < minPrice) include = false;
                        }
                        if (include && maxPrice != null) {
                            Double tp = booking.getTotalprice();
                            if (tp == null || tp > maxPrice) include = false;
                        }

                        if (include && depositPaidFilter != null) {
                            Boolean dp = booking.getDepositpaid();
                            if (dp == null || !dp.equals(depositPaidFilter)) include = false;
                        }
                    } catch (Exception e) {
                        logger.warn("Error while applying filters for booking {}: {}", bookingId, e.getMessage());
                        include = false;
                    }

                    if (!include) continue;

                    // Persist booking (triggers Booking workflow)
                    try {
                        CompletableFuture<UUID> idFuture = entityService.addItem(
                            Booking.ENTITY_NAME,
                            Booking.ENTITY_VERSION,
                            booking
                        );
                        // wait for persistence to complete
                        idFuture.get();
                        persisted.add(booking);
                    } catch (Exception e) {
                        logger.error("Failed to persist booking {}: {}", bookingId, e.getMessage(), e);
                    }
                }
            }

            // After processing all bookings, update job status to next workflow state
            // As per workflow, after FETCHING it goes to FILTERING
            entity.setStatus("FILTERING");

            logger.info("FetchBookingsProcessor persisted {} bookings for job '{}'", persisted.size(), entity.getName());

        } catch (Exception ex) {
            logger.error("Error fetching bookings: {}", ex.getMessage(), ex);
            try {
                entity.setStatus("FAILED");
            } catch (Exception e) {
                logger.warn("Unable to set status to FAILED: {}", e.getMessage());
            }
            return entity;
        }

        return entity;
    }
}