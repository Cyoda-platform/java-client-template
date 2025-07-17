package com.java_template.controller;

import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping(path = "/prototype/digest")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    public Controller(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @PostMapping("/requests") // must be first
    public ResponseEntity<CreateDigestResponse> createOrUpdateDigestRequest(
            @RequestBody @Valid DigestRequestInput input) {
        try {
            DigestRequest dr = new DigestRequest();
            dr.setEmail(input.getEmail());
            dr.setMetadata(input.getMetadata() == null ? "" : input.getMetadata());
            dr.setStatus(DigestRequest.Status.RECEIVED);
            dr.setCreatedAt(Instant.now());

            // Persist entity with entityService
            entityService.addItem(dr);

            logger.info("Created DigestRequest id={}", dr.getId());

            // Business logic moved to processors - removed from here

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreateDigestResponse(dr.getId(), dr.getStatus().name()));
        } catch (Exception ex) {
            logger.error("Error creating DigestRequest", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create DigestRequest");
        }
    }

    @GetMapping("/requests/{id}") // must be first
    public ResponseEntity<DigestRequestDetailsResponse> getDigestRequest(
            @PathVariable @NotBlank @Pattern(regexp="\\d+") String id) {
        try {
            DigestRequest dr = entityService.getDigestRequestById(id);
            if (dr == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
            }
            DigestData dd = entityService.getDigestDataByRequestId(id);
            EmailDispatch ed = entityService.getEmailDispatchByRequestId(id);

            DigestDataResponse ddr = dd == null ? null
                    : new DigestDataResponse(dd.getFormat().name(), dd.getData());
            EmailDispatchResponse edr = ed == null ? null
                    : new EmailDispatchResponse(ed.getStatus().name(), ed.getSentAt());

            DigestRequestDetailsResponse resp = new DigestRequestDetailsResponse(
                    dr.getId(), dr.getEmail(), dr.getMetadata(), dr.getStatus().name(), ddr, edr);

            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException ex) {
            logger.error("Error fetching DigestRequest id={}: {}", id, ex.getStatusCode());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error fetching DigestRequest id={}", id, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch DigestRequest");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return new ResponseEntity<>(errorBody, ex.getStatusCode());
    }

    // DTOs and inner classes copied from prototype

    public static class DigestRequestInput {
        @NotBlank
        @Email
        private String email;
        @Size(max = 1000)
        private String metadata;

        public String getEmail() {
            return email;
        }
        public void setEmail(String email) {
            this.email = email;
        }
        public String getMetadata() {
            return metadata;
        }
        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }
    }

    public static class CreateDigestResponse {
        private final String id;
        private final String status;

        public CreateDigestResponse(String id, String status) {
            this.id = id;
            this.status = status;
        }
        public String getId() {
            return id;
        }
        public String getStatus() {
            return status;
        }
    }

    public static class DigestRequestDetailsResponse {
        private final String id;
        private final String email;
        private final String metadata;
        private final String status;
        private final DigestDataResponse digestData;
        private final EmailDispatchResponse emailDispatch;

        public DigestRequestDetailsResponse(String id, String email, String metadata, String status,
                                            DigestDataResponse digestData, EmailDispatchResponse emailDispatch) {
            this.id = id;
            this.email = email;
            this.metadata = metadata;
            this.status = status;
            this.digestData = digestData;
            this.emailDispatch = emailDispatch;
        }
        public String getId() {
            return id;
        }
        public String getEmail() {
            return email;
        }
        public String getMetadata() {
            return metadata;
        }
        public String getStatus() {
            return status;
        }
        public DigestDataResponse getDigestData() {
            return digestData;
        }
        public EmailDispatchResponse getEmailDispatch() {
            return emailDispatch;
        }
    }

    public static class DigestDataResponse {
        private final String format;
        private final String data;

        public DigestDataResponse(String format, String data) {
            this.format = format;
            this.data = data;
        }
        public String getFormat() {
            return format;
        }
        public String getData() {
            return data;
        }
    }

    public static class EmailDispatchResponse {
        private final String status;
        private final Instant sentAt;

        public EmailDispatchResponse(String status, Instant sentAt) {
            this.status = status;
            this.sentAt = sentAt;
        }
        public String getStatus() {
            return status;
        }
        public Instant getSentAt() {
            return sentAt;
        }
    }

}