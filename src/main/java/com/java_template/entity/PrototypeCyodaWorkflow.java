Certainly! The key is to minimize controller logic by moving all asynchronous processing, filtering, sorting, and any additional data enrichment into the workflow function `processFlight`. The controller will then just accept the request, build a minimal "shell" entity (or even the raw request data wrapped as an ObjectNode), and call `entityService.addItem` passing the workflow function. The workflow function will do all heavy lifting asynchronously right before persistence.

Below is the updated complete Java code reflecting these changes:

- The controller’s search endpoint **only** constructs the initial entity (an ObjectNode wrapping the request and some metadata like a searchId).
- The workflow `processFlight` will fetch flights, apply filters, sorting, enrich the entity with results and metadata.
- The workflow will also cache the result (fire-and-forget).
- Persistence of the enriched entity happens automatically after workflow completes.
- The controller’s GET endpoint remains as is for fetching cached search results.
- The workflow receives an `ObjectNode` entity and modifies it directly.

---

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaFlights")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SearchResultResponse> searchResultsCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final String ENTITY_NAME = "flight";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> searchFlights(@RequestBody @Valid SearchRequest request) {
        logger.info("Received flight search request: {} -> {} on {}, passengers {}",
                request.getDepartureAirport(), request.getArrivalAirport(),
                request.getDepartureDate(), request.getPassengers());

        String searchId = UUID.randomUUID().toString();

        // Build initial entity as ObjectNode with search request + metadata
        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("searchId", searchId);
        entity.set("request", objectMapper.valueToTree(request));
        entity.put("status", "processing");

        // Add item with workflow function which will do all async processing
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entity,
                this::processFlight // workflow function will enrich entity asynchronously before saving
        );

        // Return accepted with empty results + searchId
        SearchResultResponse emptyResponse = new SearchResultResponse(Collections.emptyList(), 0, searchId);
        return ResponseEntity.accepted().body(emptyResponse);
    }

    @GetMapping(value = "/results/{searchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> getCachedResults(@PathVariable @NotBlank String searchId) {
        logger.info("Retrieving cached flight search results for {}", searchId);
        SearchResultResponse result = searchResultsCache.get(searchId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search ID not found");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Workflow function: processes the flight search entity asynchronously before persistence.
     * This includes:
     * - Extracting search request from the entity
     * - Fetching mock flights
     * - Applying filters and sorting
     * - Enriching entity with flights array, total count, final status
     * - Caching the result for quick retrieval
     * 
     * @param entity the entity as ObjectNode (mutable)
     * @return processed entity (same instance)
     */
    private ObjectNode processFlight(ObjectNode entity) {
        String searchId = entity.path("searchId").asText(null);
        if (searchId == null) {
            logger.error("processFlight: No searchId found in entity");
            entity.put("status", "error");
            return entity;
        }

        try {
            JsonNode requestNode = entity.path("request");
            if (requestNode.isMissingNode() || !requestNode.isObject()) {
                logger.error("processFlight: Invalid or missing request data");
                entity.put("status", "error");
                return entity;
            }

            SearchRequest request = objectMapper.treeToValue(requestNode, SearchRequest.class);
            logger.info("processFlight: start processing searchId {}", searchId);

            // Fetch flights (simulate delay)
            List<FlightInfo> flights = mockFetchFlights(request);

            // Apply filters and sorting
            flights = applyFilters(flights, request);
            flights = applySorting(flights, request.getSortBy(), request.getSortOrder());

            // Add results to entity
            ArrayNode flightsArray = objectMapper.createArrayNode();
            for (FlightInfo f : flights) {
                flightsArray.add(objectMapper.valueToTree(f));
            }

            entity.set("flights", flightsArray);
            entity.put("totalResults", flights.size());
            entity.put("status", "completed");

            // Cache the SearchResultResponse for GET endpoint
            searchResultsCache.put(searchId, new SearchResultResponse(flights, flights.size(), searchId));

            logger.info("processFlight: completed processing {} flights for searchId {}", flights.size(), searchId);
        } catch (Exception e) {
            logger.error("processFlight: error processing searchId {}: {}", searchId, e.getMessage(), e);
            entity.put("status", "error");
            // Put empty flights array to be safe
            entity.set("flights", objectMapper.createArrayNode());
            entity.put("totalResults", 0);

            searchResultsCache.put(searchId, new SearchResultResponse(Collections.emptyList(), 0, searchId));
        }
        return entity;
    }

    // Simulate fetching flights (mock)
    private List<FlightInfo> mockFetchFlights(SearchRequest request) throws InterruptedException {
        Thread.sleep(500); // simulate delay
        List<FlightInfo> flights = new ArrayList<>();
        flights.add(new FlightInfo("FL-001","Delta Air Lines","DL123",
                request.getDepartureAirport(),request.getArrivalAirport(),
                request.getDepartureDate()+"T07:00:00Z",request.getDepartureDate()+"T10:00:00Z",250.00,"USD"));
        flights.add(new FlightInfo("FL-002","American Airlines","AA456",
                request.getDepartureAirport(),request.getArrivalAirport(),
                request.getDepartureDate()+"T09:30:00Z",request.getDepartureDate()+"T12:30:00Z",230.00,"USD"));
        flights.add(new FlightInfo("FL-003","United Airlines","UA789",
                request.getDepartureAirport(),request.getArrivalAirport(),
                request.getDepartureDate()+"T12:00:00Z",request.getDepartureDate()+"T15:00:00Z",270.00,"USD"));
        return flights;
    }

    private List<FlightInfo> applyFilters(List<FlightInfo> flights, SearchRequest req) {
        List<FlightInfo> filtered = new ArrayList<>(flights);
        if (req.getAirlines() != null && !req.getAirlines().isEmpty()) {
            Set<String> allowed = new HashSet<>();
            req.getAirlines().forEach(a -> allowed.add(a.toLowerCase()));
            filtered.removeIf(f -> !allowed.contains(f.getAirline().toLowerCase()));
        }
        if (req.getMaxPrice() != null) {
            filtered.removeIf(f -> f.getPrice() > req.getMaxPrice());
        }
        if (req.getDepartureTimeFrom() != null && req.getDepartureTimeTo() != null) {
            String from = req.getDepartureTimeFrom();
            String to = req.getDepartureTimeTo();
            filtered.removeIf(f -> {
                String dep = f.getDepartureTime().substring(11,16);
                return dep.compareTo(from) < 0 || dep.compareTo(to) > 0;
            });
        }
        return filtered;
    }

    private List<FlightInfo> applySorting(List<FlightInfo> flights, String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.isEmpty()) return flights;
        Comparator<FlightInfo> cmp = null;
        switch (sortBy) {
            case "price": cmp = Comparator.comparingDouble(FlightInfo::getPrice); break;
            case "departureTime": cmp = Comparator.comparing(FlightInfo::getDepartureTime); break;
            case "arrivalTime": cmp = Comparator.comparing(FlightInfo::getArrivalTime); break;
        }
        if (cmp == null) return flights;
        if ("desc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        flights.sort(cmp);
        return flights;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleError(ResponseStatusException e) {
        logger.error("Error {}: {}", e.getStatusCode(), e.getReason());
        Map<String,String> error = new HashMap<>();
        error.put("error", e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(error);
    }

    // DTOs

    public static class SearchRequest {
        @NotBlank
        @Pattern(regexp="^[A-Z]{3}$")
        private String departureAirport;

        @NotBlank
        @Pattern(regexp="^[A-Z]{3}$")
        private String arrivalAirport;

        @NotBlank
        @Pattern(regexp="^\\d{4}-\\d{2}-\\d{2}$")
        private String departureDate;

        @Pattern(regexp="^\\d{4}-\\d{2}-\\d{2}$")
        private String returnDate;

        @Min(1)
        private int passengers;

        private List<@NotBlank @Pattern(regexp="^[A-Z]{2,3}$") String> airlines;

        @DecimalMin("0.0")
        private Double maxPrice;

        @Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$")
        private String departureTimeFrom;

        @Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$")
        private String departureTimeTo;

        @Pattern(regexp="^(price|departureTime|arrivalTime)$")
        private String sortBy;

        @Pattern(regexp="^(asc|desc)$")
        private String sortOrder;

        // Getters and setters omitted for brevity

        public String getDepartureAirport() { return departureAirport; }
        public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }
        public String getArrivalAirport() { return arrivalAirport; }
        public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }
        public String getDepartureDate() { return departureDate; }
        public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }
        public String getReturnDate() { return returnDate; }
        public void setReturnDate(String returnDate) { this.returnDate = returnDate; }
        public int getPassengers() { return passengers; }
        public void setPassengers(int passengers) { this.passengers = passengers; }
        public List<String> getAirlines() { return airlines; }
        public void setAirlines(List<String> airlines) { this.airlines = airlines; }
        public Double getMaxPrice() { return maxPrice; }
        public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }
        public String getDepartureTimeFrom() { return departureTimeFrom; }
        public void setDepartureTimeFrom(String departureTimeFrom) { this.departureTimeFrom = departureTimeFrom; }
        public String getDepartureTimeTo() { return departureTimeTo; }
        public void setDepartureTimeTo(String departureTimeTo) { this.departureTimeTo = departureTimeTo; }
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class SearchResultResponse {
        private List<FlightInfo> flights;
        private int totalResults;
        private String searchId;

        public SearchResultResponse(List<FlightInfo> flights, int totalResults, String searchId) {
            this.flights = flights;
            this.totalResults = totalResults;
            this.searchId = searchId;
        }

        // Getters and setters
        public List<FlightInfo> getFlights() { return flights; }
        public void setFlights(List<FlightInfo> flights) { this.flights = flights; }
        public int getTotalResults() { return totalResults; }
        public void setTotalResults(int totalResults) { this.totalResults = totalResults; }
        public String getSearchId() { return searchId; }
        public void setSearchId(String searchId) { this.searchId = searchId; }
    }

    public static class FlightInfo {
        private String flightId;
        private String airline;
        private String flightNumber;
        private String departureAirport;
        private String arrivalAirport;
        private String departureTime;
        private String arrivalTime;
        private double price;
        private String currency;

        public FlightInfo() {}

        public FlightInfo(String flightId, String airline, String flightNumber,
                          String departureAirport, String arrivalAirport,
                          String departureTime, String arrivalTime,
                          double price, String currency) {
            this.flightId = flightId;
            this.airline = airline;
            this.flightNumber = flightNumber;
            this.departureAirport = departureAirport;
            this.arrivalAirport = arrivalAirport;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
            this.price = price;
            this.currency = currency;
        }

        // Getters and setters omitted for brevity
        public String getFlightId() { return flightId; }
        public void setFlightId(String flightId) { this.flightId = flightId; }
        public String getAirline() { return airline; }
        public void setAirline(String airline) { this.airline = airline; }
        public String getFlightNumber() { return flightNumber; }
        public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
        public String getDepartureAirport() { return departureAirport; }
        public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }
        public String getArrivalAirport() { return arrivalAirport; }
        public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }
        public String getDepartureTime() { return departureTime; }
        public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }
        public String getArrivalTime() { return arrivalTime; }
        public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}
```

---

### Summary of changes:

- **Controller POST /search**: only creates a minimal entity (ObjectNode with request data + searchId + status) and calls `addItem` with `processFlight` workflow.
- **Workflow `processFlight`**: asynchronously invoked by entityService on the entity before persistence. This function:
  - Extracts the request,
  - Performs the entire search logic (fetch, filter, sort),
  - Modifies the entity by adding flights array, total results, and status,
  - Caches the result for GET retrieval.
- The controller GET endpoint just returns cached results.
- All async processing moved out of controller and into the workflow function, which is the correct, robust approach per requirements.
- The entity passed to workflow is mutable `ObjectNode` and is modified in-place.
- The workflow cannot call add/update/delete on the same entity model but can add other entities if needed (not used here).
- Logging and error handling moved mostly into workflow.

With this architecture, the controller is slim and all complex logic is handled in the workflow function, which runs asynchronously and atomically with the persistence action.

If you want, I can also provide test cases or further refinements.