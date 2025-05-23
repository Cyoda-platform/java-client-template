```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/flights")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Simple in-memory cache for search results: searchId -> SearchResultResponse
    private final Map<String, SearchResultResponse> searchResultsCache = new ConcurrentHashMap<>();

    // TODO: Replace with your real Airport Gap API key
    private static final String AIRPORT_GAP_API_KEY = "YOUR_AIRPORT_GAP_API_KEY";

    // Base endpoint for Airport Gap Flight Search (Note: Airport Gap API mainly exposes airports, airlines, but no direct flight search)
    // For prototype purposes, we simulate flight search via placeholder or mock data.
    // TODO: Replace with real flight search API endpoint or integrate a partner API for flight search.
    private static final String AIRPORT_GAP_API_BASE = "https://airportgap.com/api/v1";

    // POST /api/flights/search
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> searchFlights(@Valid @RequestBody SearchRequest request) {
        log.info("Received flight search request: departure {} -> arrival {}, date {}, passengers {}",
                request.getDepartureAirport(), request.getArrivalAirport(), request.getDepartureDate(), request.getPassengers());

        // Basic validation
        if (!StringUtils.hasText(request.getDepartureAirport()) || !StringUtils.hasText(request.getArrivalAirport())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Departure and arrival airports must be specified");
        }
        if (request.getPassengers() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passengers must be at least 1");
        }
        if (!StringUtils.hasText(request.getDepartureDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Departure date must be specified");
        }

        // Generate a unique searchId
        String searchId = UUID.randomUUID().toString();

        // Fire-and-forget async processing to call external API and process results
        CompletableFuture.runAsync(() -> processFlightSearch(request, searchId))
                .exceptionally(ex -> {
                    log.error("Error processing flight search for searchId {}: {}", searchId, ex.getMessage());
                    // In a real app, mark the job as failed etc.
                    return null;
                });

        // Return immediately with empty results + searchId (simulate async behavior)
        SearchResultResponse emptyResponse = new SearchResultResponse(Collections.emptyList(), 0, searchId);
        return ResponseEntity.accepted().body(emptyResponse);
    }

    // GET /api/flights/results/{searchId}
    @GetMapping(value = "/results/{searchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> getCachedResults(@PathVariable String searchId) {
        log.info("Retrieving cached flight search results for searchId {}", searchId);
        SearchResultResponse result = searchResultsCache.get(searchId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search ID not found");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Async background processing of flight search:
     * - Calls Airport Gap API (mocked here)
     * - Applies filters and sorting
     * - Caches results under searchId
     */
    @Async
    void processFlightSearch(SearchRequest request, String searchId) {
        log.info("Processing flight search asynchronously for searchId {}", searchId);
        List<FlightInfo> flights;
        try {
            // TODO: Replace this mock call with real external API integration
            flights = mockFetchFlights(request);
            // Apply filters
            flights = applyFilters(flights, request.getFilters());
            // Apply sorting
            flights = applySorting(flights, request.getSortBy(), request.getSortOrder());

            SearchResultResponse response = new SearchResultResponse(flights, flights.size(), searchId);
            searchResultsCache.put(searchId, response);
            log.info("Flight search processing finished for searchId {}, found {} flights", searchId, flights.size());

        } catch (Exception e) {
            log.error("Failed to process flight search for searchId {}: {}", searchId, e.getMessage());
            // On error, store empty results with error info or remove cache entry
            searchResultsCache.put(searchId, new SearchResultResponse(Collections.emptyList(), 0, searchId));
        }
    }

    /**
     * Mock method to simulate fetching flights.
     * In reality, Airport Gap API does not provide flight search,
     * so this is a placeholder returning dummy data.
     */
    private List<FlightInfo> mockFetchFlights(SearchRequest request) throws IOException, InterruptedException {
        log.info("Mock fetching flights for {} -> {} on {}", request.getDepartureAirport(), request.getArrivalAirport(), request.getDepartureDate());

        // Simulate delay
        Thread.sleep(500);

        // A few dummy flights
        List<FlightInfo> flights = new ArrayList<>();
        flights.add(new FlightInfo("FL-001", "Delta Air Lines", "DL123", request.getDepartureAirport(), request.getArrivalAirport(),
                request.getDepartureDate() + "T07:00:00Z", request.getDepartureDate() + "T10:00:00Z", 250.00, "USD"));
        flights.add(new FlightInfo("FL-002", "American Airlines", "AA456", request.getDepartureAirport(), request.getArrivalAirport(),
                request.getDepartureDate() + "T09:30:00Z", request.getDepartureDate() + "T12:30:00Z", 230.00, "USD"));
        flights.add(new FlightInfo("FL-003", "United Airlines", "UA789", request.getDepartureAirport(), request.getArrivalAirport(),
                request.getDepartureDate() + "T12:00:00Z", request.getDepartureDate() + "T15:00:00Z", 270.00, "USD"));

        return flights;
    }

    private List<FlightInfo> applyFilters(List<FlightInfo> flights, Filters filters) {
        if (filters == null) return flights;
        List<FlightInfo> filtered = new ArrayList<>(flights);

        if (filters.getAirlines() != null && !filters.getAirlines().isEmpty()) {
            Set<String> allowedAirlines = new HashSet<>();
            for (String a : filters.getAirlines()) {
                allowedAirlines.add(a.toLowerCase());
            }
            filtered.removeIf(flight -> !allowedAirlines.contains(flight.getAirline().toLowerCase()));
        }
        if (filters.getMaxPrice() != null) {
            filtered.removeIf(flight -> flight.getPrice() > filters.getMaxPrice());
        }
        if (filters.getDepartureTimeRange() != null) {
            String from = filters.getDepartureTimeRange().getFrom();
            String to = filters.getDepartureTimeRange().getTo();
            filtered.removeIf(flight -> {
                String depTime = flight.getDepartureTime().substring(11, 16); // HH:mm
                return depTime.compareTo(from) < 0 || depTime.compareTo(to) > 0;
            });
        }
        return filtered;
    }

    private List<FlightInfo> applySorting(List<FlightInfo> flights, String sortBy, String sortOrder) {
        if (!StringUtils.hasText(sortBy)) return flights;
        Comparator<FlightInfo> comparator = null;

        switch (sortBy.toLowerCase()) {
            case "price":
                comparator = Comparator.comparingDouble(FlightInfo::getPrice);
                break;
            case "departuretime":
                comparator = Comparator.comparing(FlightInfo::getDepartureTime);
                break;
            case "arrivaltime":
                comparator = Comparator.comparing(FlightInfo::getArrivalTime);
                break;
        }
        if (comparator == null) return flights;

        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        flights.sort(comparator);
        return flights;
    }

    // Basic exception handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException e) {
        log.error("Handling error: {} - {}", e.getStatusCode(), e.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(error);
    }

    // --- DTO classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String departureAirport;
        private String arrivalAirport;
        private String departureDate;
        private String returnDate; // null for one-way trips
        private int passengers;
        private Filters filters;
        private String sortBy;    // "price" | "departureTime" | "arrivalTime"
        private String sortOrder; // "asc" | "desc"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filters {
        private List<String> airlines;
        private Double maxPrice;
        private TimeRange departureTimeRange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeRange {
        private String from; // "HH:mm"
        private String to;   // "HH:mm"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResultResponse {
        private List<FlightInfo> flights;
        private int totalResults;
        private String searchId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlightInfo {
        private String flightId;
        private String airline;
        private String flightNumber;
        private String departureAirport;
        private String arrivalAirport;
        private String departureTime; // ISO8601 datetime string
        private String arrivalTime;   // ISO8601 datetime string
        private double price;
        private String currency;
    }
}
```
