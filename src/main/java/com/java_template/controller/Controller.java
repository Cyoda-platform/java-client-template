package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Path("/cyoda-pets")
@Tag(name = "Cyoda Pets", description = "Operations related to pets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject
    EntityService entityService;

    private static final String ENTITY_NAME = "Pet";

    public static class Pet {
        public UUID technicalId;
        public String name;
        public String category;
        public String status;
        public List<String> photoUrls;
    }

    public static class SearchRequest {
        @Pattern(regexp = "available|pending|sold")
        public String status;

        @Size(max = 30)
        public String category;

        @Size(max = 50)
        public String nameContains;
    }

    public static class AddPetRequest {
        @NotBlank
        @Size(max = 100)
        public String name;

        @NotBlank
        @Size(max = 30)
        public String category;

        @Pattern(regexp = "available|pending|sold")
        public String status;

        @NotNull
        @Size(min = 1)
        public List<@NotBlank String> photoUrls;
    }

    public static class FavoriteRequest {
        @NotNull
        @Positive
        public Long userId;
    }

    public static class MessageResponse {
        public String message;

        public MessageResponse() {}

        public MessageResponse(String message) {
            this.message = message;
        }
    }

    private final Map<Long, Set<UUID>> userFavorites = new HashMap<>();

    @POST
    @Path("/search")
    @Operation(summary = "Search pets by status, category, and name")
    public Response searchPets(@RequestBody @Valid SearchRequest searchRequest) throws IOException, InterruptedException {
        String statusParam = Optional.ofNullable(searchRequest.status).orElse("available");
        URI uri = URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new WebApplicationException("External API error", Response.Status.BAD_GATEWAY);
        }

        JsonNode rootNode = objectMapper.readTree(response.body());
        List<Pet> filteredPets = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode petNode : rootNode) {
                Pet pet = parsePetFromJson(petNode);
                if (searchRequest.category != null && !searchRequest.category.equalsIgnoreCase(pet.category))
                    continue;
                if (searchRequest.nameContains != null &&
                        (pet.name == null || !pet.name.toLowerCase(Locale.ROOT).contains(searchRequest.nameContains.toLowerCase(Locale.ROOT))))
                    continue;
                filteredPets.add(pet);
            }
        }
        Map<String, List<Pet>> result = Collections.singletonMap("pets", filteredPets);
        return Response.ok(result).build();
    }

    @POST
    @Operation(summary = "Add a new pet")
    public Response addPet(@RequestBody @Valid AddPetRequest addPetRequest) throws ExecutionException, InterruptedException {
        ObjectNode petEntity = objectMapper.createObjectNode();
        petEntity.put("name", addPetRequest.name);
        petEntity.put("category", addPetRequest.category);
        petEntity.put("status", Optional.ofNullable(addPetRequest.status).orElse("available"));
        petEntity.putArray("photoUrls").addAll(objectMapper.valueToTree(addPetRequest.photoUrls));

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                petEntity
        );

        UUID technicalId = idFuture.get();
        petEntity.put("technicalId", technicalId.toString());

        Map<String, Object> resp = new HashMap<>();
        resp.put("technicalId", technicalId);
        resp.put("message", "Pet added successfully");

        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get pet by ID")
    public Response getPetById(@PathParam UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id
        );
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            throw new WebApplicationException("Pet not found", Response.Status.NOT_FOUND);
        }

        Pet pet = objectMapper.convertValue(itemNode, Pet.class);
        if (itemNode.has("technicalId")) {
            try {
                pet.technicalId = UUID.fromString(itemNode.get("technicalId").asText());
            } catch (IllegalArgumentException ignored) {
                pet.technicalId = null;
            }
        }
        return Response.ok(pet).build();
    }

    @POST
    @Path("/{id}/favorite")
    @Operation(summary = "Mark pet as favorite for a user")
    public Response markFavorite(@PathParam UUID id, @RequestBody @Valid FavoriteRequest favoriteRequest) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id
        );
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            throw new WebApplicationException("Pet not found", Response.Status.NOT_FOUND);
        }

        userFavorites.computeIfAbsent(favoriteRequest.userId, k -> new HashSet<>()).add(id);

        return Response.ok(new MessageResponse("Pet marked as favorite")).build();
    }

    private Pet parsePetFromJson(JsonNode petNode) {
        Pet pet = new Pet();
        if (petNode.has("id")) {
            pet.technicalId = UUID.nameUUIDFromBytes(Long.toString(petNode.get("id").asLong()).getBytes());
        }
        pet.name = petNode.path("name").asText(null);
        JsonNode categoryNode = petNode.path("category");
        if (categoryNode.isObject()) {
            pet.category = categoryNode.path("name").asText(null);
        }
        pet.status = petNode.path("status").asText(null);
        List<String> photos = new ArrayList<>();
        JsonNode photosNode = petNode.path("photoUrls");
        if (photosNode.isArray()) {
            for (JsonNode photoUrlNode : photosNode) {
                photos.add(photoUrlNode.asText());
            }
        }
        pet.photoUrls = photos;
        return pet;
    }
}