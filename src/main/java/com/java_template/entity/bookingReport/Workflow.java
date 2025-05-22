package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<ObjectNode> processbookingReport(ObjectNode entity) {
        // workflow orchestration only
        return processFetchAndFilter(entity)
                .thenCompose(this::processCalculateReport)
                .thenApply(report -> {
                    entity.set("report", report);
                    return entity;
                });
    }

    public CompletableFuture<ObjectNode> processFetchAndFilter(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateFrom = getTextOrNull(entity, "dateFrom");
                String dateTo = getTextOrNull(entity, "dateTo");
                Double minTotalPrice = getDoubleOrNull(entity, "minTotalPrice");
                Double maxTotalPrice = getDoubleOrNull(entity, "maxTotalPrice");
                Boolean depositPaid = getBooleanOrNull(entity, "depositPaid");

                JsonNode bookingIdsNode = restTemplate.getForObject(URI.create("https://restful-booker.herokuapp.com/booking"), JsonNode.class);
                if (bookingIdsNode == null || !bookingIdsNode.isArray()) {
                    throw new RuntimeException("Failed to retrieve bookings");
                }
                List<Integer> bookingIds = new ArrayList<>();
                for (JsonNode node : bookingIdsNode) {
                    if (node.has("bookingid")) bookingIds.add(node.get("bookingid").asInt());
                    else if (node.has("id")) bookingIds.add(node.get("id").asInt());
                }

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

                List<ObjectNode> filtered = allBookings.stream()
                        .filter(b -> filterBooking(b, dateFrom, dateTo, minTotalPrice, maxTotalPrice, depositPaid))
                        .collect(Collectors.toList());

                ObjectNode result = entity.objectNode();
                ArrayNode bookingsArray = entity.arrayNode();
                bookingsArray.addAll(filtered);
                result.set("filteredBookings", bookingsArray);
                // Save filter params for next stage
                if (dateFrom != null) result.put("dateFrom", dateFrom);
                if (dateTo != null) result.put("dateTo", dateTo);
                return result;
            } catch (Exception e) {
                logger.error("Error in processFetchAndFilter: ", e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<ObjectNode> processCalculateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode filteredBookingsNode = entity.get("filteredBookings");
                if (filteredBookingsNode == null || !filteredBookingsNode.isArray()) {
                    throw new RuntimeException("No filtered bookings found for report calculation");
                }
                List<ObjectNode> filteredBookings = new ArrayList<>();
                filteredBookingsNode.forEach(n -> {
                    if (n.isObject()) filteredBookings.add((ObjectNode) n);
                });

                String dateFrom = getTextOrNull(entity, "dateFrom");
                String dateTo = getTextOrNull(entity, "dateTo");

                double totalRevenue = 0;
                int count = filteredBookings.size();
                for (ObjectNode b : filteredBookings) {
                    totalRevenue += b.path("totalprice").asDouble(0);
                }
                double averagePrice = count > 0 ? totalRevenue / count : 0;

                int bookingsWithinDateRange = count;
                if (dateFrom != null && dateTo != null) {
                    LocalDate from = parseDateOrNull(dateFrom);
                    LocalDate to = parseDateOrNull(dateTo);
                    bookingsWithinDateRange = (int) filteredBookings.stream()
                            .filter(b -> {
                                LocalDate checkin = parseDateOrNull(b.path("bookingdates").path("checkin").asText(null));
                                LocalDate checkout = parseDateOrNull(b.path("bookingdates").path("checkout").asText(null));
                                if (checkin == null || checkout == null) return false;
                                return !(checkout.isBefore(from) || checkin.isAfter(to));
                            }).count();
                }

                ObjectNode report = entity.objectNode();
                report.put("totalBookings", count);
                report.put("totalRevenue", round(totalRevenue, 2));
                report.put("averageBookingPrice", round(averagePrice, 2));
                report.put("bookingsWithinDateRange", bookingsWithinDateRange);

                return report;
            } catch (Exception e) {
                logger.error("Error in processCalculateReport: ", e);
                throw new RuntimeException(e);
            }
        });
    }

    private boolean filterBooking(ObjectNode b, String dateFrom, String dateTo, Double minTotalPrice, Double maxTotalPrice, Boolean depositPaid) {
        if (depositPaid != null && b.path("depositpaid").asBoolean(false) != depositPaid) return false;
        double price = b.path("totalprice").asDouble(0);
        if (minTotalPrice != null && price < minTotalPrice) return false;
        if (maxTotalPrice != null && price > maxTotalPrice) return false;

        LocalDate from = parseDateOrNull(dateFrom);
        LocalDate to = parseDateOrNull(dateTo);
        JsonNode bookingdates = b.path("bookingdates");
        if (bookingdates.isMissingNode()) return false;
        LocalDate checkin = parseDateOrNull(bookingdates.path("checkin").asText(null));
        LocalDate checkout = parseDateOrNull(bookingdates.path("checkout").asText(null));
        if (checkin == null || checkout == null) return false;
        if (from != null && checkout.isBefore(from)) return false;
        if (to != null && checkin.isAfter(to)) return false;
        return true;
    }

    private String getTextOrNull(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        return n.asText(null);
    }

    private Double getDoubleOrNull(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.doubleValue();
        try {
            return Double.parseDouble(n.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean getBooleanOrNull(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        if (n.isBoolean()) return n.booleanValue();
        String text = n.asText().toLowerCase();
        if ("true".equals(text)) return true;
        if ("false".equals(text)) return false;
        return null;
    }

    private LocalDate parseDateOrNull(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private double round(double val, int places) {
        long factor = (long) Math.pow(10, places);
        return Math.round(val * factor) / (double) factor;
    }
}