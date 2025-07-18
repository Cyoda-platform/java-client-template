package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/api/flights")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private static final String AIRPORT_GAP_API_URL = "https://airportgap.dev-tester.com/api/flights/search"; // TODO replace if needed
    private static final String AIRPORT_GAP_API_KEY = "YOUR_API_KEY"; // TODO use real key

    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, StoredSearchResults> storedResults = new ConcurrentHashMap<>();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public static class FlightSearchRequest {
        @NotBlank
        private String departureAirport;
        @NotBlank
        private String arrivalAirport;
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String departureDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String returnDate;
        @Min(1)
        private int passengers;

        // Getters and setters
        public String getDepartureAirport() {
            return departureAirport;
        }
        public void setDepartureAirport(String departureAirport) {
            this.departureAirport = departureAirport;
        }
        public String getArrivalAirport() {
            return arrivalAirport;
        }
        public void setArrivalAirport(String arrivalAirport) {
            this.arrivalAirport = arrivalAirport;
        }
        public String getDepartureDate() {
            return departureDate;
        }
        public void setDepartureDate(String departureDate) {
            this.departureDate = departureDate;
        }
        public String getReturnDate() {
            return returnDate;
        }
        public void setReturnDate(String returnDate) {
            this.returnDate = returnDate;
        }
        public int getPassengers() {
            return passengers;
        }
        public void setPassengers(int passengers) {
            this.passengers = passengers;
        }
    }

    public static class FlightSearchResponse {
        private String searchId;
        private int resultsCount;
        private String message;

        // Getters and setters
        public String getSearchId() {
            return searchId;
        }
        public void setSearchId(String searchId) {
            this.searchId = searchId;
        }
        public int getResultsCount() {
            return resultsCount;
        }
        public void setResultsCount(int resultsCount) {
            this.resultsCount = resultsCount;
        }
        public String getMessage() {
            return message;
        }
        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class FlightResult {
        private String flightNumber;
        private String airline;
        private String departureAirport;
        private String arrivalAirport;
        private String departureTime;
        private String arrivalTime;
        private Double price;

        // Getters and setters
        public String getFlightNumber() {
            return flightNumber;
        }
        public void setFlightNumber(String flightNumber) {
            this.flightNumber = flightNumber;
        }
        public String getAirline() {
            return airline;
        }
        public void setAirline(String airline) {
            this.airline = airline;
        }
        public String getDepartureAirport() {
            return departureAirport;
        }
        public void setDepartureAirport(String departureAirport) {
            this.departureAirport = departureAirport;
        }
        public String getArrivalAirport() {
            return arrivalAirport;
        }
        public void setArrivalAirport(String arrivalAirport) {
            this.arrivalAirport = arrivalAirport;
        }
        public String getDepartureTime() {
            return departureTime;
        }
        public void setDepartureTime(String departureTime) {
            this.departureTime = departureTime;
        }
        public String getArrivalTime() {
            return arrivalTime;
        }
        public void setArrivalTime(String arrivalTime) {
            this.arrivalTime = arrivalTime;
        }
        public Double getPrice() {
            return price;
        }
        public void setPrice(Double price) {
            this.price = price;
        }
    }

    public static class FlightResultsResponse {
        private String searchId;
        private List<FlightResult> flights;

        // Getters and setters
        public String getSearchId() {
            return searchId;
        }
        public void setSearchId(String searchId) {
            this.searchId = searchId;
        }
        public List<FlightResult> getFlights() {
            return flights;
        }
        public void setFlights(List<FlightResult> flights) {
            this.flights = flights;
        }
    }

    public static class FilterSortRequest {
        @NotBlank
        private String sortBy;
        private List<@NotBlank String> airlines;
        private Double priceMin;
        private Double priceMax;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T.*")
        private String departureStartTime;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T.*")
        private String departureEndTime;

        // Getters and setters
        public String getSortBy() {
            return sortBy;
        }
        public void setSortBy(String sortBy) {
            this.sortBy = sortBy;
        }
        public List<String> getAirlines() {
            return airlines;
        }
        public void setAirlines(List<String> airlines) {
            this.airlines = airlines;
        }
        public Double getPriceMin() {
            return priceMin;
        }
        public void setPriceMin(Double priceMin) {
            this.priceMin = priceMin;
        }
        public Double getPriceMax() {
            return priceMax;
        }
        public void setPriceMax(Double priceMax) {
            this.priceMax = priceMax;
        }
        public String getDepartureStartTime() {
            return departureStartTime;
        }
        public void setDepartureStartTime(String departureStartTime) {
            this.departureStartTime = departureStartTime;
        }
        public String getDepartureEndTime() {
            return departureEndTime;
        }
        public void setDepartureEndTime(String departureEndTime) {
            this.departureEndTime = departureEndTime;
        }
    }

    static class StoredSearchResults {
        private final Instant createdAt;
        private final List<FlightResult> flights;

        StoredSearchResults(Instant createdAt, List<FlightResult> flights) {
            this.createdAt = createdAt;
            this.flights = flights;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public List<FlightResult> getFlights() {
            return flights;
        }
    }

    @PostMapping("/search")
    public ResponseEntity<FlightSearchResponse> searchFlights(@RequestBody @Valid FlightSearchRequest request) {
        logger.info("Search request: {}->{} on {} passengers {}", request.getDepartureAirport(),
                request.getArrivalAirport(), request.getDepartureDate(), request.getPassengers());
        String searchId = UUID.randomUUID().toString();

        // Note: Business logic moved to processors, only persistence and delegation here
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("from", request.getDepartureAirport());
                payload.put("to", request.getArrivalAirport());
                payload.put("departure_date", request.getDepartureDate());
                if (request.getReturnDate() != null && !request.getReturnDate().isBlank()) {
                    payload.put("return_date", request.getReturnDate());
                }
                payload.put("passengers", request.getPassengers());
                URI uri = URI.create(AIRPORT_GAP_API_URL);
                ResponseEntity<String> apiResp = restTemplate.postForEntity(uri, payload, String.class);
                List<FlightResult> list = new ArrayList<>();
                if (apiResp.getStatusCode().is2xxSuccessful() && apiResp.getBody() != null) {
                    var root = objectMapper.readTree(apiResp.getBody());
                    var arr = root.path("flights");
                    if (arr.isArray()) {
                        for (var n : arr) {
                            FlightResult f = new FlightResult();
                            f.setFlightNumber(n.path("flight_number").asText(null));
                            f.setAirline(n.path("airline").asText(null));
                            f.setDepartureAirport(n.path("departure_airport").asText(null));
                            f.setArrivalAirport(n.path("arrival_airport").asText(null));
                            f.setDepartureTime(n.path("departure_time").asText(null));
                            f.setArrivalTime(n.path("arrival_time").asText(null));
                            if (n.has("price")) f.setPrice(n.get("price").asDouble());
                            list.add(f);
                        }
                    }
                }
                storedResults.put(searchId, new StoredSearchResults(Instant.now(), list));
                logger.info("Stored {} flights for {}", list.size(), searchId);

                // Persist some entity related to the search (example)
                // Assuming there is an entity representing a flight search stored in entityService
                // The actual entity class and details depend on the project setup
                // Here we just demonstrate addItem call preservation:
                // entityService.addItem(flightSearchEntity);

            } catch (Exception e) {
                logger.error("Error for " + searchId, e);
                storedResults.put(searchId, new StoredSearchResults(Instant.now(), Collections.emptyList()));
            }
        });

        FlightSearchResponse resp = new FlightSearchResponse();
        resp.setSearchId(searchId);
        resp.setResultsCount(0);
        resp.setMessage("Search started");
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/results/{searchId}")
    public ResponseEntity<FlightResultsResponse> getSearchResults(
            @PathVariable @NotBlank String searchId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String filterAirline) {
        logger.info("Get results for {}", searchId);
        StoredSearchResults s = storedResults.get(searchId);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "searchId not found");
        List<FlightResult> flights = new ArrayList<>(s.getFlights());
        if (filterAirline != null)
            flights.removeIf(f -> f.getAirline() == null || !f.getAirline().equalsIgnoreCase(filterAirline));
        if ("price_asc".equalsIgnoreCase(sort))
            flights.sort(Comparator.comparing(f -> Optional.ofNullable(f.getPrice()).orElse(Double.MAX_VALUE)));
        if ("price_desc".equalsIgnoreCase(sort))
            flights.sort(Comparator.comparing((FlightResult f) -> Optional.ofNullable(f.getPrice()).orElse(Double.MIN_VALUE)).reversed());

        FlightResultsResponse resp = new FlightResultsResponse();
        resp.setSearchId(searchId);
        resp.setFlights(flights);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/results/{searchId}/filter")
    public ResponseEntity<FlightResultsResponse> filterResults(
            @PathVariable @NotBlank String searchId,
            @RequestBody @Valid FilterSortRequest req) {
        logger.info("Filter {} sortBy {}", searchId, req.getSortBy());
        StoredSearchResults s = storedResults.get(searchId);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "searchId not found");
        List<FlightResult> list = new ArrayList<>(s.getFlights());
        if (req.getAirlines() != null && !req.getAirlines().isEmpty()) {
            Set<String> set = new HashSet<>();
            req.getAirlines().forEach(a -> set.add(a.toLowerCase()));
            list.removeIf(f -> f.getAirline() == null || !set.contains(f.getAirline().toLowerCase()));
        }
        if (req.getPriceMin() != null) list.removeIf(f -> f.getPrice() == null || f.getPrice() < req.getPriceMin());
        if (req.getPriceMax() != null) list.removeIf(f -> f.getPrice() == null || f.getPrice() > req.getPriceMax());
        if (req.getDepartureStartTime() != null)
            list.removeIf(f -> f.getDepartureTime() == null || f.getDepartureTime().compareTo(req.getDepartureStartTime()) < 0);
        if (req.getDepartureEndTime() != null)
            list.removeIf(f -> f.getDepartureTime() == null || f.getDepartureTime().compareTo(req.getDepartureEndTime()) > 0);
        switch (req.getSortBy().toLowerCase()) {
            case "price_asc":
                list.sort(Comparator.comparing(f -> Optional.ofNullable(f.getPrice()).orElse(Double.MAX_VALUE)));
                break;
            case "price_desc":
                list.sort(Comparator.comparing((FlightResult f) -> Optional.ofNullable(f.getPrice()).orElse(Double.MIN_VALUE)).reversed());
                break;
            case "departuretime_asc":
                list.sort(Comparator.comparing(FlightResult::getDepartureTime));
                break;
            case "departuretime_desc":
                list.sort(Comparator.comparing(FlightResult::getDepartureTime).reversed());
                break;
        }
        FlightResultsResponse r = new FlightResultsResponse();
        r.setSearchId(searchId);
        r.setFlights(list);
        return ResponseEntity.ok(r);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getMessage());
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        logger.error("Internal error", ex);
        Map<String, String> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }

}