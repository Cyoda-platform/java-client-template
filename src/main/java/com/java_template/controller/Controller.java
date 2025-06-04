package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        logger.info("Controller initialized");
    }

    @PostMapping(value = "/directors", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Director createDirector(@RequestBody @Valid DirectorCreateRequest request) {
        Director director = new Director(null, request.getName(), request.getBirthDate(), request.getNationality(), new ArrayList<>());
        CompletableFuture<UUID> idFuture = entityService.addItem("Director", ENTITY_VERSION, director);
        UUID techId = idFuture.join();
        director.setTechnicalId(techId);
        logger.info("Director created with technicalId {}", techId);
        return director;
    }

    @GetMapping(value = "/directors/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Director getDirector(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Director", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Director not found");
        }
        Director director = JsonUtil.convertObjectNodeToDirector(node);
        String condition = String.format("{\"directorId\":\"%s\"}", id.toString());
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Movie", ENTITY_VERSION, condition);
        ArrayNode moviesNodes = filteredItemsFuture.join();
        List<Movie> directedMovies = new ArrayList<>();
        for (int i = 0; i < moviesNodes.size(); i++) {
            ObjectNode movieNode = (ObjectNode) moviesNodes.get(i);
            directedMovies.add(JsonUtil.convertObjectNodeToMovie(movieNode));
        }
        director.setMovies(directedMovies);
        return director;
    }

    @PostMapping(value = "/movies", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Movie createMovie(@RequestBody @Valid MovieCreateRequest request) {
        Movie movie = new Movie(null, request.getName(), request.getYear(), request.getGenre(), request.getDirectorId(), request.getNominations());
        CompletableFuture<UUID> idFuture = entityService.addItem("Movie", ENTITY_VERSION, movie);
        UUID techId = idFuture.join();
        movie.setTechnicalId(techId);
        logger.info("Movie created with technicalId {}", techId);
        return movie;
    }

    @GetMapping(value = "/movies/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MovieDetailsResponse getMovie(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Movie", ENTITY_VERSION, id);
        ObjectNode movieNode = itemFuture.join();
        if (movieNode == null || movieNode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
        }
        Movie movie = JsonUtil.convertObjectNodeToMovie(movieNode);
        CompletableFuture<ObjectNode> directorFuture = entityService.getItem("Director", ENTITY_VERSION, movie.getDirectorId());
        ObjectNode directorNode = directorFuture.join();
        if (directorNode == null || directorNode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Director data missing");
        }
        Director director = JsonUtil.convertObjectNodeToDirector(directorNode);
        DirectorSummary directorSummary = new DirectorSummary(director.getTechnicalId(), director.getName());
        return new MovieDetailsResponse(
                movie.getTechnicalId(),
                movie.getName(),
                movie.getYear(),
                movie.getGenre(),
                directorSummary,
                movie.getNominations()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        error.put("timestamp", OffsetDateTime.now().toString());
        return error;
    }

    private class JsonUtil {

        private Director convertObjectNodeToDirector(ObjectNode node) {
            try {
                Director director = objectMapper.treeToValue(node, Director.class);
                if (node.has("technicalId") && !node.get("technicalId").isNull()) {
                    director.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                }
                return director;
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Director data");
            }
        }

        private Movie convertObjectNodeToMovie(ObjectNode node) {
            try {
                Movie movie = objectMapper.treeToValue(node, Movie.class);
                if (node.has("technicalId") && !node.get("technicalId").isNull()) {
                    movie.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                }
                return movie;
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Movie data");
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Director {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "birthDate must be YYYY-MM-DD")
        private String birthDate;
        private String nationality;
        private List<Movie> movies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Movie {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        private Integer year;
        private String genre;
        private UUID directorId;
        private List<Nomination> nominations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Nomination {
        @NotBlank
        private String awardName;
        @NotNull
        @Min(1900)
        @Max(2100)
        private Integer year;
    }

    @Data
    @NoArgsConstructor
    public static class DirectorCreateRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "birthDate must be YYYY-MM-DD")
        private String birthDate;
        @Size(max = 50)
        private String nationality;
    }

    @Data
    @NoArgsConstructor
    public static class MovieCreateRequest {
        @NotBlank
        @Size(max = 200)
        private String name;
        @NotNull
        @Min(1888)
        @Max(2100)
        private Integer year;
        @NotBlank
        @Size(max = 100)
        private String genre;
        @NotNull
        private UUID directorId;
        @NotNull
        @Size(min = 1)
        private List<@Valid Nomination> nominations;
    }

    @Data
    @AllArgsConstructor
    public static class DirectorSummary {
        private UUID id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class MovieDetailsResponse {
        private UUID id;
        private String name;
        private Integer year;
        private String genre;
        private DirectorSummary director;
        private List<Nomination> nominations;
    }
}