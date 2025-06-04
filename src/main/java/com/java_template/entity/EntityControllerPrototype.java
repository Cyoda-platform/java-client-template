package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
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

    @PostMapping(value = "/directors", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Director createDirector(@RequestBody @Valid DirectorCreateRequest request) {
        // validation annotations handle input checks
        UUID id = UUID.randomUUID();
        Director director = new Director(id, request.getName(), request.getBirthDate(), request.getNationality(), new ArrayList<>());
        directors.put(id, director);
        logger.info("Director created with id {}", id);
        return director;
    }

    @GetMapping(value = "/directors/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Director getDirector(@PathVariable UUID id) {
        Director director = directors.get(id);
        if (director == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Director not found");
        }
        List<Movie> directedMovies = new ArrayList<>();
        for (Movie movie : movies.values()) {
            if (movie.getDirectorId().equals(id)) {
                directedMovies.add(movie);
            }
        }
        director.setMovies(directedMovies);
        return director;
    }

    @PostMapping(value = "/movies", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Movie createMovie(@RequestBody @Valid MovieCreateRequest request) {
        UUID id = UUID.randomUUID();
        Movie movie = new Movie(id, request.getName(), request.getYear(), request.getGenre(), request.getDirectorId(), request.getNominations());
        movies.put(id, movie);
        logger.info("Movie created with id {}", id);
        fireAndForgetProcessing(movie); // TODO: integrate real event/workflow
        return movie;
    }

    @GetMapping(value = "/movies/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MovieDetailsResponse getMovie(@PathVariable UUID id) {
        Movie movie = movies.get(id);
        if (movie == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
        }
        Director director = directors.get(movie.getDirectorId());
        if (director == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Director data missing");
        }
        return new MovieDetailsResponse(
                movie.getId(),
                movie.getName(),
                movie.getYear(),
                movie.getGenre(),
                new DirectorSummary(director.getId(), director.getName()),
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

    @Async
    public void fireAndForgetProcessing(Movie movie) {
        CompletableFuture.runAsync(() -> {
            logger.info("Async processing for movie id {}", movie.getId());
            // TODO: implement event-driven workflow
        });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Director {
        private UUID id;
        private String name;
        private String birthDate;
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
        @Pattern(regexp = "^\d{4}-\d{2}-\d{2}$", message = "birthDate must be YYYY-MM-DD")
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
