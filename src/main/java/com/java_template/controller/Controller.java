package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda/bookings")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    // Entity model constants
    private static final String ENTITY_NAME_REPORT = "bookingReport";
    private static final String ENTITY_NAME_BOOKING = "booking";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * POST endpoint to create a bookingReport entity.
     * Accepts filter parameters as JSON ObjectNode.
     * Passes entity to entityService.addItem.
     * Returns persisted entity enriched with report data.
     */
    @PostMapping("/filter-report")
    public CompletableFuture<ResponseEntity<JsonNode>> filterAndReport(@RequestBody ObjectNode filterRequest) {
        logger.info("Received filter-report request: {}", filterRequest);
        return entityService.addItem(
                ENTITY_NAME_REPORT,
                ENTITY_VERSION,
                filterRequest
        ).thenApply(id -> {
            // entityService.addItem returns UUID of persisted entity
            // The entity is expected to have report data
            return ResponseEntity.ok(filterRequest);
        }).exceptionally(ex -> {
            logger.error("Error in filterAndReport: ", ex);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report");
        });
    }

    /**
     * Example method showing how to add Booking entity without workflow function.
     * Accepts validated booking entity as ObjectNode, returns future UUID.
     */
    public CompletableFuture<UUID> addBookingEntity(ObjectNode bookingEntity) {
        return entityService.addItem(
                ENTITY_NAME_BOOKING,
                ENTITY_VERSION,
                bookingEntity
        );
    }

    // --- Helper methods ---

    private boolean filterBooking(ObjectNode booking, String dateFrom, String dateTo,
                                  Double minTotalPrice, Double maxTotalPrice, Boolean depositPaid) {
        if (depositPaid != null) {
            JsonNode depNode = booking.get("depositpaid");
            if (depNode == null || depNode.isNull() || depNode.isMissingNode()) return false;
            if (depNode.isBoolean()) {
                if (depNode.booleanValue() != depositPaid) return false;
            } else {
                if (!Boolean.valueOf(depNode.asText()).equals(depositPaid)) return false;
            }
        }
        double totalPrice = booking.path("totalprice").asDouble(Double.NaN);
        if (!Double.isNaN(totalPrice)) {
            if (minTotalPrice != null && totalPrice < minTotalPrice) return false;
            if (maxTotalPrice != null && totalPrice > maxTotalPrice) return false;
        }

        ObjectNode bookingdates = booking.with("bookingdates");
        LocalDate checkin = parseDateOrNull(bookingdates.path("checkin").asText(null));
        LocalDate checkout = parseDateOrNull(bookingdates.path("checkout").asText(null));
        if (checkin == null || checkout == null) return false;

        LocalDate fromDate = parseDateOrNull(dateFrom);
        LocalDate toDate = parseDateOrNull(dateTo);
        if (fromDate != null && checkout.isBefore(fromDate)) return false;
        if (toDate != null && checkin.isAfter(toDate)) return false;

        return true;
    }

    private ObjectNode calculateReport(List<ObjectNode> filtered, String dateFrom, String dateTo) {
        ObjectNode report = objectMapper.createObjectNode();
        int totalBookings = filtered.size();
        double totalRevenue = filtered.stream()
                .mapToDouble(b -> b.path("totalprice").asDouble(0))
                .sum();
        double average = totalBookings > 0 ? totalRevenue / totalBookings : 0;

        LocalDate from = parseDateOrNull(dateFrom);
        LocalDate to = parseDateOrNull(dateTo);
        int withinRange = 0;
        if(from != null && to != null) {
            withinRange = (int) filtered.stream().filter(b -> {
                ObjectNode bookingdates = b.with("bookingdates");
                LocalDate ci = parseDateOrNull(bookingdates.path("checkin").asText(null));
                LocalDate co = parseDateOrNull(bookingdates.path("checkout").asText(null));
                if (ci == null || co == null) return false;
                return !(co.isBefore(from) || ci.isAfter(to));
            }).count();
        } else {
            withinRange = totalBookings;
        }

        report.put("totalBookings", totalBookings);
        report.put("totalRevenue", round(totalRevenue, 2));
        report.put("averageBookingPrice", round(average, 2));
        report.put("bookingsWithinDateRange", withinRange);

        ArrayNode bookingsArray = objectMapper.createArrayNode();
        filtered.forEach(bookingsArray::add);
        report.set("bookings", bookingsArray);

        return report;
    }

    private LocalDate parseDateOrNull(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String getTextOrNull(ObjectNode node, String field) {
        JsonNode val = node.get(field);
        return (val != null && !val.isNull()) ? val.asText(null) : null;
    }

    private Double getDoubleOrNull(ObjectNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        if (val.isNumber()) return val.doubleValue();
        try {
            return Double.parseDouble(val.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean getBooleanOrNull(ObjectNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        if (val.isBoolean()) return val.booleanValue();
        return Boolean.parseBoolean(val.asText());
    }

    private double round(double val, int places) {
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(val * factor) / factor;
    }

}