package com.java_template.controller;

import com.java_template.util.JsonUtil;
import com.java_template.entity.Director;
import com.java_template.entity.Movie;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final JsonUtil jsonUtil = new JsonUtil();

    private final ConcurrentHashMap directors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap movies = new ConcurrentHashMap<>();

    @PostMapping("/directors")
    public Director createDirector(@RequestBody ObjectNode node) {
        Director director = jsonUtil.convertObjectNodeToDirector(node);
        directors.put(director.getId(), node);
        logger.info("Director created: {}", director.getName());
        return director;
    }

    @GetMapping("/directors/{id}")
    public Director getDirector(@PathVariable UUID id) {
        ObjectNode node = directors.get(id);
        if (node == null) {
            throw new RuntimeException("Director not found");
        }
        Director director = jsonUtil.convertObjectNodeToDirector(node);
        List directedMovies = new ArrayList<>();
        movies.forEach((movieId, movieNode) -> {
            if (movieNode.hasNonNull("directorId") && movieNode.get("directorId").asText().equals(id.toString())) {
                directedMovies.add(jsonUtil.convertObjectNodeToMovie(movieNode));
            }
        });
        director.setMovies(directedMovies);
        return director;
    }

    @PostMapping("/movies")
    public Movie createMovie(@RequestBody ObjectNode movieNode) {
        Movie movie = jsonUtil.convertObjectNodeToMovie(movieNode);
        movies.put(movie.getId(), movieNode);
        logger.info("Movie created: {}", movie.getName());
        return movie;
    }

    @GetMapping("/movies/{id}")
    public Movie getMovie(@PathVariable UUID id) {
        ObjectNode movieNode = movies.get(id);
        if (movieNode == null) {
            throw new RuntimeException("Movie not found");
        }
        Movie movie = jsonUtil.convertObjectNodeToMovie(movieNode);
        ObjectNode directorNode = directors.get(UUID.fromString(movieNode.get("directorId").asText()));
        if (directorNode == null) {
            throw new RuntimeException("Director not found");
        }
        movie.setDirector(jsonUtil.convertObjectNodeToDirector(directorNode));
        return movie;
    }
}

