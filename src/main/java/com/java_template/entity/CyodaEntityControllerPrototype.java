package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaFlights")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SearchResultResponse> searchResultsCache = new ConcurrentHashMap<>();
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
        CompletableFuture.runAsync(() -> processFlightSearch(request, searchId))
                .exceptionally(ex -> {
                    logger.error("Error processing flight search for {}: {}", searchId, ex.getMessage());
                    return null;
                });
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

    @Async
    void processFlightSearch(SearchRequest request, String searchId) {
        logger.info("Processing flight search for {}", searchId);
        try {
            List<FlightInfo> flights = mockFetchFlights(request);
            flights = applyFilters(flights, request);
            flights = applySorting(flights, request.getSortBy(), request.getSortOrder());
            SearchResultResponse response = new SearchResultResponse(flights, flights.size(), searchId);
            searchResultsCache.put(searchId, response);
            logger.info("Finished processing {} flights for {}", flights.size(), searchId);
        } catch (Exception e) {
            logger.error("Failed to process flight search for {}: {}", searchId, e.getMessage());
            searchResultsCache.put(searchId, new SearchResultResponse(Collections.emptyList(), 0, searchId));
        }
    }

    private List<FlightInfo> mockFetchFlights(SearchRequest request) throws InterruptedException {
        Thread.sleep(500);
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
        if (!StringUtils.hasText(sortBy)) return flights;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
        private String departureTime;
        private String arrivalTime;
        private double price;
        private String currency;
    }
}