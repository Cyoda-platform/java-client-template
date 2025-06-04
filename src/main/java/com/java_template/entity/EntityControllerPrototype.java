```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<UUID, Director> directors = new ConcurrentHashMap<>();
    private final Map<UUID, Movie> movies = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    // --------------------------------------
    // Director APIs
    // --------------------------------------

    @PostMapping(value = "/directors", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Director createDirector(@RequestBody DirectorCreateRequest request) {
        logger.info("Received request to create director: {}", request.getName());

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Director name must be provided");
        }

        UUID id = UUID.randomUUID();
        Director director = new Director(id, request.getName(), request.getBirthDate(), request.getNationality(), new ArrayList<>());
        directors.put(id, director);

        logger.info("Director created with id {}", id);
        return director;
    }

    @GetMapping(value = "/directors/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Director getDirector(@PathVariable UUID id) {
        logger.info("Fetching director with id {}", id);
        Director director = directors.get(id);
        if (director == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Director not found");
        }
        // Populate movies list from movies map filtering by directorId
        List<Movie> directedMovies = new ArrayList<>();
        for (Movie movie : movies.values()) {
            if (movie.getDirectorId().equals(id)) {
                directedMovies.add(movie);
            }
        }
        director.setMovies(directedMovies);
        return director;
    }

    // --------------------------------------
    // Movie APIs
    // --------------------------------------

    @PostMapping(value = "/movies", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Movie createMovie(@RequestBody MovieCreateRequest request) {
        logger.info("Received request to create movie: {}", request.getName());

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movie name must be provided");
        }
        if (request.getDirectorId() == null || !directors.containsKey(request.getDirectorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid directorId must be provided");
        }
        if (request.getNominations() == null) {
            request.setNominations(new ArrayList<>());
        }

        UUID id = UUID.randomUUID();
        Movie movie = new Movie(id, request.getName(), request.getYear(), request.getGenre(), request.getDirectorId(), request.getNominations());
        movies.put(id, movie);

        logger.info("Movie created with id {}", id);

        // TODO: If any external processing or event-driven workflow is needed, trigger it asynchronously here
        // fireAndForgetProcessing(movie);

        return movie;
    }

    @GetMapping(value = "/movies/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MovieDetailsResponse getMovie(@PathVariable UUID id) {
        logger.info("Fetching movie with id {}", id);
        Movie movie = movies.get(id);
        if (movie == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
        }
        Director director = directors.get(movie.getDirectorId());
        if (director == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Director data corrupted or missing");
        }
        MovieDetailsResponse response = new MovieDetailsResponse(
                movie.getId(),
                movie.getName(),
                movie.getYear(),
                movie.getGenre(),
                new DirectorSummary(director.getId(), director.getName()),
                movie.getNominations()
        );
        return response;
    }

    // --------------------------------------
    // Exception Handling (minimal)
    // --------------------------------------

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        error.put("timestamp", OffsetDateTime.now().toString());
        return error;
    }

    // --------------------------------------
    // DTOs and Entities
    // --------------------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Director {
        private UUID id;
        private String name;
        private LocalDate birthDate;
        private String nationality;
        private List<Movie> movies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Movie {
        private UUID id;
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
        private String awardName;
        private Integer year;
    }

    @Data
    public static class DirectorCreateRequest {
        private String name;
        private LocalDate birthDate;
        private String nationality;
    }

    @Data
    public static class MovieCreateRequest {
        private String name;
        private Integer year;
        private String genre;
        private UUID directorId;
        private List<Nomination> nominations;
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

    // --------------------------------------
    // Async processing placeholder
    // --------------------------------------

    @Async
    public void fireAndForgetProcessing(Movie movie) {
        CompletableFuture.runAsync(() -> {
            logger.info("Started async processing for movie id {}", movie.getId());
            // TODO: Implement actual event-driven workflow or external API calls here
            try {
                Thread.sleep(1000); // simulate delay
            } catch (InterruptedException e) {
                logger.error("Async processing interrupted", e);
            }
            logger.info("Completed async processing for movie id {}", movie.getId());
        });
    }
}
```