```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/api/flights")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String AIRPORT_GAP_API_URL = "https://airportgap.dev-tester.com/api/flights/search"; // TODO: Replace if production URL differs
    private static final String AIRPORT_GAP_API_KEY = "YOUR_API_KEY"; // TODO: Replace with real API key or config

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage of search results keyed by searchId
    private final Map<String, StoredSearchResults> storedResults = new ConcurrentHashMap<>();

    // --- DTOs ---

    @Data
    public static class FlightSearchRequest {
        @NotBlank
        private String departureAirport;

        @NotBlank
        private String arrivalAirport;

        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "departureDate must be in YYYY-MM-DD format")
        private String departureDate;

        // Optional for round-trip; null if one-way
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "returnDate must be in YYYY-MM-DD format")
        private String returnDate;

        @Min(1)
        private int passengers;
    }

    @Data
    public static class FlightSearchResponse {
        private String searchId;
        private int resultsCount;
        private String message;
    }

    @Data
    public static class FlightResult {
        private String flightNumber;
        private String airline;
        private String departureAirport;
        private String arrivalAirport;
        private String departureTime; // ISO8601 datetime string
        private String arrivalTime;   // ISO8601 datetime string
        private Double price;
    }

    @Data
    public static class FlightResultsResponse {
        private String searchId;
        private List<FlightResult> flights;
    }

    @Data
    public static class FilterSortRequest {
        @NotBlank
        private String sortBy; // e.g. price_asc, departureTime_desc

        private Filters filters;

        @Data
        public static class Filters {
            private List<String> airlines;
            private PriceRange priceRange;
            private DepartureTimeRange departureTimeRange;
        }

        @Data
        public static class PriceRange {
            private Double min;
            private Double max;
        }

        @Data
        public static class DepartureTimeRange {
            private String start; // ISO8601 datetime
            private String end;   // ISO8601 datetime
        }
    }

    // Wrapper for stored results
    @Data
    static class StoredSearchResults {
        private final Instant createdAt;
        private final List<FlightResult> flights;
    }

    // --- Endpoints ---

    /**
     * POST /prototype/api/flights/search
     * Accepts search parameters, queries external API asynchronously, stores results.
     */
    @PostMapping("/search")
    public ResponseEntity<FlightSearchResponse> searchFlights(@Valid @RequestBody FlightSearchRequest request) {
        logger.info("Received flight search request: departure {} arrival {} date {} passengers {}",
                request.getDepartureAirport(), request.getArrivalAirport(), request.getDepartureDate(), request.getPassengers());

        // Generate unique searchId
        String searchId = UUID.randomUUID().toString();

        // Fire-and-forget querying external API (async background task)
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting external API query for searchId {}", searchId);

                // Build external API request payload (according to Airport Gap API docs)
                Map<String, Object> apiRequestPayload = new HashMap<>();
                apiRequestPayload.put("from", request.getDepartureAirport());
                apiRequestPayload.put("to", request.getArrivalAirport());
                apiRequestPayload.put("departure_date", request.getDepartureDate());
                if (request.getReturnDate() != null && !request.getReturnDate().isBlank()) {
                    apiRequestPayload.put("return_date", request.getReturnDate());
                }
                apiRequestPayload.put("passengers", request.getPassengers());

                // TODO: replace with proper HTTP client with headers, timeout, retries, etc.
                // For demo purposes, using RestTemplate with simple exchange

                // Note: Airport Gap API might require API key or auth - TODO: add headers if needed
                URI uri = URI.create(AIRPORT_GAP_API_URL);

                // Send POST request to Airport Gap API
                ResponseEntity<String> apiResponse = restTemplate.postForEntity(uri, apiRequestPayload, String.class);

                if (!apiResponse.getStatusCode().is2xxSuccessful()) {
                    logger.error("Airport Gap API call failed with status: {}", apiResponse.getStatusCodeValue());
                    storedResults.put(searchId, new StoredSearchResults(Instant.now(), Collections.emptyList()));
                    return;
                }

                String body = apiResponse.getBody();
                if (body == null || body.isBlank()) {
                    logger.error("Airport Gap API returned empty body");
                    storedResults.put(searchId, new StoredSearchResults(Instant.now(), Collections.emptyList()));
                    return;
                }

                // Parse JSON response
                JsonNode rootNode = objectMapper.readTree(body);

                // TODO: Adapt parsing based on actual Airport Gap API response schema
                // Here we assume results are under "flights" array; if no flights, empty array

                JsonNode flightsNode = rootNode.path("flights");
                List<FlightResult> flightResults = new ArrayList<>();

                if (flightsNode.isArray()) {
                    for (JsonNode flightNode : flightsNode) {
                        FlightResult fr = new FlightResult();
                        fr.setFlightNumber(flightNode.path("flight_number").asText(null));
                        fr.setAirline(flightNode.path("airline").asText(null));
                        fr.setDepartureAirport(flightNode.path("departure_airport").asText(null));
                        fr.setArrivalAirport(flightNode.path("arrival_airport").asText(null));
                        fr.setDepartureTime(flightNode.path("departure_time").asText(null));
                        fr.setArrivalTime(flightNode.path("arrival_time").asText(null));
                        if (flightNode.has("price")) {
                            fr.setPrice(flightNode.get("price").asDouble(0.0));
                        }
                        flightResults.add(fr);
                    }
                }

                storedResults.put(searchId, new StoredSearchResults(Instant.now(), flightResults));
                logger.info("Stored {} flights for searchId {}", flightResults.size(), searchId);

            } catch (Exception ex) {
                logger.error("Error querying Airport Gap API for searchId " + searchId, ex);
                storedResults.put(searchId, new StoredSearchResults(Instant.now(), Collections.emptyList()));
            }
        });

        FlightSearchResponse response = new FlightSearchResponse();
        response.setSearchId(searchId);
        response.setResultsCount(0); // results not ready yet
        response.setMessage("Search started. Use searchId to retrieve results.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /prototype/api/flights/results/{searchId}
     * Retrieve stored flight search results.
     */
    @GetMapping("/results/{searchId}")
    public ResponseEntity<FlightResultsResponse> getSearchResults(@PathVariable String searchId,
                                                                 @RequestParam(required = false) String sort,
                                                                 @RequestParam(required = false) String filterAirline) {
        logger.info("Retrieving results for searchId {}", searchId);

        StoredSearchResults stored = storedResults.get(searchId);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "searchId not found");
        }

        List<FlightResult> flights = new ArrayList<>(stored.getFlights());

        // Basic filtering by airline if requested
        if (filterAirline != null && !filterAirline.isBlank()) {
            flights.removeIf(f -> f.getAirline() == null || !f.getAirline().equalsIgnoreCase(filterAirline));
        }

        // Basic sorting
        if (sort != null) {
            switch (sort.toLowerCase()) {
                case "price_asc":
                    flights.sort(Comparator.comparing(f -> Optional.ofNullable(f.getPrice()).orElse(Double.MAX_VALUE)));
                    break;
                case "price_desc":
                    flights.sort(Comparator.comparing((FlightResult f) -> Optional.ofNullable(f.getPrice()).orElse(Double.MIN_VALUE)).reversed());
                    break;
                case "departuretime_asc":
                    flights.sort(Comparator.comparing(FlightResult::getDepartureTime, Comparator.nullsLast(String::compareTo)));
                    break;
                case "departuretime_desc":
                    flights.sort(Comparator.comparing(FlightResult::getDepartureTime, Comparator.nullsLast(String::compareTo)).reversed());
                    break;
                default:
                    // Unsupported sort - ignore
                    logger.info("Unsupported sort parameter: {}", sort);
            }
        }

        FlightResultsResponse response = new FlightResultsResponse();
        response.setSearchId(searchId);
        response.setFlights(flights);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /prototype/api/flights/results/{searchId}/filter
     * Apply complex filtering and sorting to stored results.
     */
    @PostMapping("/results/{searchId}/filter")
    public ResponseEntity<FlightResultsResponse> filterAndSortResults(@PathVariable String searchId,
                                                                      @Valid @RequestBody FilterSortRequest request) {
        logger.info("Filtering and sorting results for searchId {} with sortBy {}", searchId, request.getSortBy());

        StoredSearchResults stored = storedResults.get(searchId);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "searchId not found");
        }

        List<FlightResult> filtered = new ArrayList<>(stored.getFlights());

        // Apply filters
        FilterSortRequest.Filters filters = request.getFilters();
        if (filters != null) {
            // Filter airlines
            if (filters.getAirlines() != null && !filters.getAirlines().isEmpty()) {
                Set<String> allowedAirlines = new HashSet<>();
                for (String al : filters.getAirlines()) {
                    if (al != null) allowedAirlines.add(al.toLowerCase());
                }
                filtered.removeIf(f -> f.getAirline() == null || !allowedAirlines.contains(f.getAirline().toLowerCase()));
            }

            // Filter price range
            if (filters.getPriceRange() != null) {
                Double min = filters.getPriceRange().getMin();
                Double max = filters.getPriceRange().getMax();
                filtered.removeIf(f -> {
                    if (f.getPrice() == null) return true;
                    if (min != null && f.getPrice() < min) return true;
                    if (max != null && f.getPrice() > max) return true;
                    return false;
                });
            }

            // Filter departure time range
            if (filters.getDepartureTimeRange() != null) {
                String start = filters.getDepartureTimeRange().getStart();
                String end = filters.getDepartureTimeRange().getEnd();
                filtered.removeIf(f -> {
                    if (f.getDepartureTime() == null) return true;
                    if (start != null && f.getDepartureTime().compareTo(start) < 0) return true;
                    if (end != null && f.getDepartureTime().compareTo(end) > 0) return true;
                    return false;
                });
            }
        }

        // Apply sorting
        switch (request.getSortBy().toLowerCase()) {
            case "price_asc":
                filtered.sort(Comparator.comparing(f -> Optional.ofNullable(f.getPrice()).orElse(Double.MAX_VALUE)));
                break;
            case "price_desc":
                filtered.sort(Comparator.comparing((FlightResult f) -> Optional.ofNullable(f.getPrice()).orElse(Double.MIN_VALUE)).reversed());
                break;
            case "departuretime_asc":
                filtered.sort(Comparator.comparing(FlightResult::getDepartureTime, Comparator.nullsLast(String::compareTo)));
                break;
            case "departuretime_desc":
                filtered.sort(Comparator.comparing(FlightResult::getDepartureTime, Comparator.nullsLast(String::compareTo)).reversed());
                break;
            default:
                logger.info("Unsupported sortBy value: {}", request.getSortBy());
        }

        FlightResultsResponse response = new FlightResultsResponse();
        response.setSearchId(searchId);
        response.setFlights(filtered);

        return ResponseEntity.ok(response);
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```