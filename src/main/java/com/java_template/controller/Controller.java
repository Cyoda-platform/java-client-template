package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaFlights")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Concurrent cache for thread safety and quick retrieval of search results
    private final Map<String, SearchResultResponse> searchResultsCache = new ConcurrentHashMap<>();
    private static final String ENTITY_NAME = "flight";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResultResponse> searchFlights(@RequestBody @Valid SearchRequest request) {
        logger.info("Received flight search request: {} -> {} on {}, passengers {}",
                request.getDepartureAirport(), request.getArrivalAirport(),
                request.getDepartureDate(), request.getPassengers());

        String searchId = UUID.randomUUID().toString();

        // Create initial entity as mutable ObjectNode to hold request and metadata
        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("searchId", searchId);
        entity.set("request", objectMapper.valueToTree(request));
        entity.put("status", "processing");

        // Call entityService.addItem without workflow argument
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entity
        );

        // Return accepted response immediately with empty results and the searchId for polling
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

    private List<FlightInfo> mockFetchFlights(SearchRequest request) throws InterruptedException {
        Thread.sleep(500); // simulate network or processing delay
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
            req.getAirlines().forEach(a -> allowed.add(a.toLowerCase(Locale.ROOT)));
            filtered.removeIf(f -> !allowed.contains(f.getAirline().toLowerCase(Locale.ROOT)));
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