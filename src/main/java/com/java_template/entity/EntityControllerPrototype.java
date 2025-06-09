import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/api/pets")
@Validated
public class EntityControllerPrototype {

    private final Map<String, List<Pet>> petDataStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/fetch") // must be first
    public ResponseEntity<?> fetchAndTransformPetDetails(@RequestBody @Valid FetchRequest request) {
        try {
            // TODO: Replace with actual external API call
            String externalApiUrl = "https://app.swaggerhub.com/apis/WinBeyond/PetstorePetstore/1.0.0#/pet/findPetsByStatus";
            ResponseEntity<String> response = restTemplate.postForEntity(externalApiUrl, request, String.class);
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            // Transform data
            List<Pet> transformedPets = transformPetData(jsonResponse);
            petDataStore.put("latest", transformedPets);

            return ResponseEntity.ok(transformedPets);

        } catch (Exception e) {
            logger.error("Error fetching and transforming pet details: {}", e.getMessage());
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @GetMapping // must be first
    public ResponseEntity<?> retrieveTransformedPetData() {
        List<Pet> pets = petDataStore.get("latest");
        if (pets == null || pets.isEmpty()) {
            return ResponseEntity.status(404).body("No pets found");
        }
        return ResponseEntity.ok(pets);
    }

    @PostMapping("/notify") // must be first
    public ResponseEntity<?> notifyNoMatchingPets(@RequestBody @Valid FetchRequest request) {
        List<Pet> pets = petDataStore.get("latest");
        if (pets == null || pets.isEmpty()) {
            logger.info("No pets match the search criteria for species: {}, status: {}, categoryId: {}",
                    request.getSpecies(), request.getStatus(), request.getCategoryId());
            return ResponseEntity.ok("No pets match the search criteria.");
        }
        return ResponseEntity.ok("Pets found.");
    }

    private List<Pet> transformPetData(JsonNode jsonResponse) {
        // TODO: Implement transformation logic
        return List.of(); // Placeholder return
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FetchRequest {
        @NotNull(message = "Species cannot be null")
        @Size(min = 1, message = "Species must not be empty")
        private String species;

        @NotNull(message = "Status cannot be null")
        @Size(min = 1, message = "Status must not be empty")
        private String status;

        @NotNull(message = "Category ID cannot be null")
        @Min(value = 1, message = "Category ID must be greater than 0")
        private Integer categoryId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Pet {
        private String name;
        private String species;
        private String status;
        private Integer categoryId;
        private String availabilityStatus;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getStatusCode().toString());
    }
}