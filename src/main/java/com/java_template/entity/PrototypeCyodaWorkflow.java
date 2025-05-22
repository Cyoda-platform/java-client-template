Certainly! To comply with the new requirement, we should refactor the controller so that all asynchronous or pre-persistence logic currently inside the controller endpoint is moved into the workflow function `process{entity_name}`.

This means:

- The controller’s responsibility shrinks to just handling HTTP requests/responses and invoking `entityService.addItem` with the validated entity and the proper workflow function.
- The workflow function (e.g. `processbooking`) will:
  - Perform any async calls (e.g. fetch external data, enrich the entity).
  - Transform or augment the entity (which is an `ObjectNode`).
  - Possibly add supplementary entities of other models via `entityService`.
  - Return the modified entity asynchronously.
- The controller does NOT perform these tasks directly anymore.

---

### What we will do:

- Change the entity parameter type from `Booking` to `ObjectNode` in the workflow.
- Move all the fetch, filtering, reporting logic into the workflow function.
- The controller will accept a filter entity (ObjectNode), pass it to `entityService.addItem` with the workflow function.
- The workflow function will:
  - Fetch bookings list and details from the external API.
  - Perform filtering based on the filter params inside the entity.
  - Build the report data inside the entity.
  - Return the modified entity (with report data) for persistence.
- The endpoint will return the final report from the persisted entity.

---

### Important:

For the `processbooking` workflow function, the entity is an `ObjectNode`.

Because the entity model in the original code was `"booking"`, but the filtering/reporting logic is not tied to adding a booking, but rather generating a report, we need to clarify the entity model.

Since your example was with the `"booking"` entity model, I assume you want to add bookings with workflow functions.

But the current controller's main endpoint is `filterAndReport` — it is about filtering bookings and generating reports, not adding bookings.

**So we likely need to create a new entity model, e.g. `"bookingFilterReport"` or `"bookingReport"` to handle this workflow.**

---

### Here's the plan for code:

- Add a new endpoint `/api/cyoda/bookings/filter-report` which will call `entityService.addItem` with:
  - `entityModel = "bookingReport"` (new entity model).
  - `entityVersion = ENTITY_VERSION`.
  - `entity` = the filter request as `ObjectNode`.
  - `workflow` = `processbookingReport`.
- Implement `processbookingReport(ObjectNode entity)` which:
  - Extracts filter parameters from `entity`.
  - Fetch bookings from external API.
  - Filter bookings.
  - Calculate report.
  - Put the report data back into `entity`.
  - Return `CompletableFuture<ObjectNode>`.
- Controller returns the report from the persisted entity.
- This moves all logic out of the controller to the workflow.

---

### Implementing this fully:

```java
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
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda/bookings")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String ENTITY_NAME_REPORT = "bookingReport";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * New POST endpoint that triggers entityService.addItem with workflow function.
     * The entity is the filter parameters as JSON.
     * The workflow does all the async and report generation work.
     */
    @PostMapping("/filter-report")
    public CompletableFuture<ResponseEntity<JsonNode>> filterAndReport(@RequestBody ObjectNode filterRequest) {
        logger.info("Received filter-report request: {}", filterRequest);
        // Add item with workflow processbookingReport
        return entityService.addItem(
                ENTITY_NAME_REPORT,
                ENTITY_VERSION,
                filterRequest,
                this::processbookingReport
        ).thenApply(id -> {
            // After persistence, ideally we fetch entity back to return report
            // But since entityService.addItem returns UUID only,
            // we return the filterRequest enriched by the workflow function, which contains report data.
            // So just return the enriched filterRequest
            return ResponseEntity.ok(filterRequest);
        });
    }

    /**
     * Workflow function for bookingReport entity.
     * This function:
     * - Fetches bookings from external API
     * - Applies filtering criteria from entity (filterRequest)
     * - Calculates report
     * - Puts report data into entity
     * - Returns modified entity asynchronously
     */
    public CompletableFuture<ObjectNode> processbookingReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Extract filter parameters from entity
                String dateFrom = getTextOrNull(entity, "dateFrom");
                String dateTo = getTextOrNull(entity, "dateTo");
                Double minTotalPrice = getDoubleOrNull(entity, "minTotalPrice");
                Double maxTotalPrice = getDoubleOrNull(entity, "maxTotalPrice");
                Boolean depositPaid = getBooleanOrNull(entity, "depositPaid");

                // Fetch booking IDs from external API
                JsonNode bookingIdsNode = restTemplate.getForObject(URI.create("https://restful-booker.herokuapp.com/booking"), JsonNode.class);
                if (bookingIdsNode == null || !bookingIdsNode.isArray()) {
                    throw new RuntimeException("Failed to retrieve bookings");
                }
                List<Integer> bookingIds = new ArrayList<>();
                for (JsonNode node : bookingIdsNode) {
                    if (node.has("bookingid")) bookingIds.add(node.get("bookingid").asInt());
                    else if (node.has("id")) bookingIds.add(node.get("id").asInt());
                }

                // Fetch booking details
                List<ObjectNode> allBookings = new ArrayList<>();
                for (Integer id : bookingIds) {
                    try {
                        JsonNode detailNode = restTemplate.getForObject("https://restful-booker.herokuapp.com/booking/{id}", JsonNode.class, id);
                        if (detailNode != null && detailNode.isObject()) {
                            ObjectNode bookingNode = (ObjectNode) detailNode.deepCopy();
                            bookingNode.put("bookingId", id);
                            allBookings.add(bookingNode);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to fetch booking detail for id={} : {}", id, ex.getMessage());
                    }
                }

                // Filter bookings
                List<ObjectNode> filtered = allBookings.stream()
                        .filter(b -> filterBooking(b, dateFrom, dateTo, minTotalPrice, maxTotalPrice, depositPaid))
                        .collect(Collectors.toList());

                // Calculate report
                ObjectNode report = calculateReport(filtered, dateFrom, dateTo);

                // Put report fields into entity
                entity.set("report", report);

                return entity;
            } catch (Exception e) {
                logger.error("Error in processbookingReport workflow: ", e);
                throw new RuntimeException(e);
            }
        });
    }

    private boolean filterBooking(ObjectNode booking, String dateFrom, String dateTo,
                                  Double minTotalPrice, Double maxTotalPrice, Boolean depositPaid) {
        if (depositPaid != null) {
            JsonNode depNode = booking.get("depositpaid");
            if (depNode == null || depNode.isNull() || depNode.isMissingNode()) return false;
            if (depNode.isBoolean()) {
                if (depNode.booleanValue() != depositPaid) return false;
            } else {
                // Try parse as string boolean
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
        int withinRange = (int) filtered.stream().filter(b -> {
            ObjectNode bookingdates = b.with("bookingdates");
            LocalDate ci = parseDateOrNull(bookingdates.path("checkin").asText(null));
            LocalDate co = parseDateOrNull(bookingdates.path("checkout").asText(null));
            if (ci == null || co == null) return false;
            return !(co.isBefore(from) || ci.isAfter(to));
        }).count();

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
```

---

### Summary of refactoring:

- The POST `/filter-report` endpoint now simply calls `entityService.addItem` with:
  - Entity model `"bookingReport"`.
  - The filter JSON as the entity.
  - The `processbookingReport` workflow function.
- All async fetching, filtering, and report calculation is done inside `processbookingReport`.
- The reporting data is put inside the same entity under `"report"` field.
- The controller returns this enriched entity as the response.
- This makes the controller lean and business logic fully inside the workflow function as required.
- `processbookingReport` is asynchronous and returns a CompletableFuture.
- The entity is an `ObjectNode`, modified directly inside the workflow function.

---

If you want me to do this for the `"booking"` entity model or others, or to handle adding bookings with workflows, let me know!