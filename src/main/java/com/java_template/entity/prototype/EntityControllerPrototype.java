package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping("/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> pets = new ConcurrentHashMap<>();
    private long nextId = 1L;

    @Data
    public static class ImportRequest {
        @Pattern(regexp = "https?://.*", message = "Must be a valid URL")
        private String sourceUrl;
    }

    @Data
    public static class ImportResponse {
        private int importedCount;
        private String status;
    }

    @Data
    public static class SearchRequest {
        @Size(max = 50)
        private String type;
        @Size(max = 20)
        private String status;
        @Size(max = 50)
        private String name;
    }

    @Data
    public static class AddPetRequest {
        @NotBlank(message = "Name is mandatory")
        private String name;
        @NotBlank(message = "Type is mandatory")
        private String type;
        @NotBlank(message = "Status is mandatory")
        private String status;
        // TODO: refine tag handling if nested objects not allowed
        private List<@NotBlank String> tags = new ArrayList<>();
    }

    @Data
    public static class AddPetResponse {
        private long id;
        private String message;
    }

    @Data
    public static class Pet {
        private long id;
        private String name;
        private String type;
        private String status;
        private List<String> tags = new ArrayList<>();
    }

    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importPets(@RequestBody @Valid ImportRequest request) {
        String defaultUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
        String sourceUrl = StringUtils.hasText(request.getSourceUrl()) ? request.getSourceUrl() : defaultUrl;
        logger.info("Importing pets from {}", sourceUrl);
        try {
            String json = restTemplate.getForObject(URI.create(sourceUrl), String.class);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "External API returned non-array");
            }
            pets.clear();
            int count = 0;
            for (JsonNode node : root) {
                Pet pet = new Pet();
                pet.setId(nextId++);
                pet.setName(node.path("name").asText("Unnamed"));
                pet.setType(node.path("category").path("name").asText("unknown"));
                pet.setStatus(node.path("status").asText("unknown"));
                List<String> t = new ArrayList<>();
                for (JsonNode tagNode : node.path("tags")) {
                    String tag = tagNode.path("name").asText(null);
                    if (tag != null) t.add(tag);
                }
                pet.setTags(t);
                pets.put(pet.getId(), pet);
                count++;
            }
            ImportResponse resp = new ImportResponse();
            resp.setImportedCount(count);
            resp.setStatus("success");
            logger.info("Imported {} pets", count);
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException ex) {
            logger.error("Import error: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected import failure", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Import failed");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid SearchRequest req) {
        logger.info("Searching pets type='{}' status='{}' name='{}'", req.getType(), req.getStatus(), req.getName());
        List<Pet> result = new ArrayList<>();
        for (Pet pet : pets.values()) {
            if ((req.getType() == null || pet.getType().equalsIgnoreCase(req.getType()))
                && (req.getStatus() == null || pet.getStatus().equalsIgnoreCase(req.getStatus()))
                && (req.getName() == null || pet.getName().toLowerCase().contains(req.getName().toLowerCase()))) {
                result.add(pet);
            }
        }
        logger.info("Found {} pets", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull @Min(1) Long id) {
        logger.info("Get pet by id {}", id);
        Pet pet = pets.get(id);
        if (pet == null) {
            logger.error("Pet {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/add")
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid AddPetRequest req) {
        logger.info("Adding pet name='{}' type='{}'", req.getName(), req.getType());
        Pet pet = new Pet();
        pet.setId(nextId++);
        pet.setName(req.getName());
        pet.setType(req.getType());
        pet.setStatus(req.getStatus());
        pet.setTags(req.getTags());
        pets.put(pet.getId(), pet);
        AddPetResponse resp = new AddPetResponse();
        resp.setId(pet.getId());
        resp.setMessage("Pet added successfully");
        logger.info("Pet {} added", pet.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() {
        logger.info("Retrieving all pets, count={}", pets.size());
        return ResponseEntity.ok(new ArrayList<>(pets.values()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String,String> err = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> err.put(e.getField(), e.getDefaultMessage()));
        logger.error("Validation errors: {}", err);
        return ResponseEntity.badRequest().body(err);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleResponseStatus(ResponseStatusException ex) {
        Map<String,String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason() != null ? ex.getReason() : "Error");
        logger.error("ResponseStatusException: {}", err);
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }
}