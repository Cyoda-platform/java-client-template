```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<UUID, Director> directors = new ConcurrentHashMap<>();
    private final Map<UUID, Movie> movies = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initData() {
        // Sample data for prototype testing
        Director d1 = new Director(UUID.randomUUID(), "Christopher Nolan", new ArrayList<>());
        Director d2 = new Director(UUID.randomUUID(), "Greta Gerwig", new ArrayList<>());

        Movie m1 = new Movie(UUID.randomUUID(), "Inception", d1.getId(),
                Arrays.asList("Best Visual Effects", "Best Sound Editing"));
        Movie m2 = new Movie(UUID.randomUUID(), "Dunkirk", d1.getId(),
                Collections.singletonList("Best Sound Mixing"));
        Movie m3 = new Movie(UUID.randomUUID(), "Lady Bird", d2.getId(),
                Collections.singletonList("Best Actress"));

        directors.put(d1.getId(), d1);
        directors.put(d2.getId(), d2);

        movies.put(m1.getId(), m1);
        movies.put(m2.getId(), m2);
        movies.put(m3.getId(), m3);

        d1.getMovies().add(m1);
        d1.getMovies().add(m2);
        d2.getMovies().add(m3);

        log.info("Initialized sample data: {} directors, {} movies", directors.size(), movies.size());
    }

    // --- Director Endpoints ---

    @GetMapping(value = "/directors", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DirectorResponse> getAllDirectors() {
        log.info("GET /directors called");
        return directors.values().stream()
                .map(this::toDirectorResponse)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/directors/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DirectorResponse getDirectorById(@PathVariable UUID id) {
        log.info("GET /directors/{} called", id);
        Director director = directors.get(id);
        if (director == null) {
            log.error("Director not found: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Director not found");
        }
        return toDirectorResponse(director);
    }

    @PostMapping(value = "/directors/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DirectorResponse> filterDirectors(@RequestBody DirectorFilterRequest filter) {
        log.info("POST /directors/filter called with filter: {}", filter);
        return directors.values().stream()
                .filter(d -> filter.getName() == null || d.getName().toLowerCase().contains(filter.getName().toLowerCase()))
                .map(this::toDirectorResponse)
                .collect(Collectors.toList());
    }

    // --- Movie Endpoints ---

    @GetMapping(value = "/movies", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MovieResponse> getAllMovies() {
        log.info("GET /movies called");
        return movies.values().stream()
                .map(this::toMovieResponse)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/movies/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MovieResponse getMovieById(@PathVariable UUID id) {
        log.info("GET /movies/{} called", id);
        Movie movie = movies.get(id);
        if (movie == null) {
            log.error("Movie not found: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
        }
        return toMovieResponse(movie);
    }

    @PostMapping(value = "/movies/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MovieResponse> filterMovies(@RequestBody MovieFilterRequest filter) {
        log.info("POST /movies/filter called with filter: {}", filter);
        return movies.values().stream()
                .filter(m -> filter.getTitle() == null || m.getTitle().toLowerCase().contains(filter.getTitle().toLowerCase()))
                .filter(m -> {
                    if (filter.getDirectorName() == null) return true;
                    Director d = directors.get(m.getDirectorId());
                    return d != null && d.getName().toLowerCase().contains(filter.getDirectorName().toLowerCase());
                })
                .filter(m -> {
                    if (filter.getAward() == null) return true;
                    return m.getAwards().stream().anyMatch(a -> a.toLowerCase().contains(filter.getAward().toLowerCase()));
                })
                .map(this::toMovieResponse)
                .collect(Collectors.toList());
    }

    // --- Mapping helpers ---

    private DirectorResponse toDirectorResponse(Director director) {
        List<MovieResponse> movieResponses = director.getMovies().stream()
                .map(this::toMovieResponse)
                .collect(Collectors.toList());
        return new DirectorResponse(director.getId(), director.getName(), movieResponses);
    }

    private MovieResponse toMovieResponse(Movie movie) {
        Director director = directors.get(movie.getDirectorId());
        DirectorSummary directorSummary = null;
        if (director != null) {
            directorSummary = new DirectorSummary(director.getId(), director.getName());
        }
        return new MovieResponse(movie.getId(), movie.getTitle(), directorSummary, movie.getAwards());
    }

    // --- Error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return error;
    }

    // --- DTOs & Entities ---

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Director {
        private UUID id;
        private String name;
        private List<Movie> movies;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Movie {
        private UUID id;
        private String title;
        private UUID directorId;
        private List<String> awards;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class DirectorResponse {
        private UUID id;
        private String name;
        private List<MovieResponse> movies;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class MovieResponse {
        private UUID id;
        private String title;
        private DirectorSummary director;
        private List<String> awards;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class DirectorSummary {
        private UUID id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    static class DirectorFilterRequest {
        private String name;
    }

    @Data
    @NoArgsConstructor
    static class MovieFilterRequest {
        private String title;
        private String directorName;
        private String award;
    }

    // --- TODO placeholders for future external API integration and async processing ---
    // For example, fire-and-forget jobs can be implemented here with @Async or CompletableFuture.runAsync(...)
}
```
