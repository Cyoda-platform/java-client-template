package com.java_template.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/cyoda/prototype")
@Validated
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Data
    public static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;
    }

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody @Valid SubscriptionRequest request) {
        // Subscription logic was minor and involved local cache only; business logic moved out - keeping minimal API response
        // No persistence call here as per original minor entity design
        logger.info("Subscription API called for email: {}", request.getEmail());
        // Respond with success, actual subscription persistence and business logic moved to processors/workflows if needed
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Subscription endpoint called", "email", request.getEmail()));
    }

    @Data
    public static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    @PostMapping(path = "/fetch-scores", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        // Business logic moved to processors/workflows; keep API signature and logging
        logger.info("Fetch scores API called for date: {}", request.getDate());
        return ResponseEntity.accepted().body(Map.of("message", "Fetch scores endpoint called", "date", request.getDate()));
    }

    // Player entity endpoints
    private static final String PLAYER_ENTITY_NAME = "Player";

    @Data
    public static class Player {
        @JsonIgnore
        private UUID technicalId;

        @NotBlank
        private String name;
        private int age;
        private String position;
    }

    @PostMapping(path = "/players", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addPlayer(@RequestBody @Valid Player player) {
        logger.info("Adding Player: {}", player.getName());
        return entityService.addItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, player);
    }

    @GetMapping(path = "/players/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Player> getPlayer(@PathVariable UUID id) {
        logger.info("Getting Player with id: {}", id);
        return entityService.getItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> objectMapper.convertValue(objectNode, Player.class));
    }

    @GetMapping(path = "/players", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<List<Player>> getAllPlayers() {
        logger.info("Getting all Players");
        return entityService.getItems(PLAYER_ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Player> players = new ArrayList<>();
                    arrayNode.forEach(node -> players.add(objectMapper.convertValue(node, Player.class)));
                    return players;
                });
    }

    @PutMapping(path = "/players/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> updatePlayer(@PathVariable UUID id, @RequestBody @Valid Player player) {
        logger.info("Updating Player with id: {}", id);
        return entityService.updateItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, id, player);
    }

    @DeleteMapping(path = "/players/{id}")
    public CompletableFuture<UUID> deletePlayer(@PathVariable UUID id) {
        logger.info("Deleting Player with id: {}", id);
        return entityService.deleteItem(PLAYER_ENTITY_NAME, ENTITY_VERSION, id);
    }

    // Team entity endpoints
    private static final String TEAM_ENTITY_NAME = "Team";

    @Data
    public static class Team {
        @JsonIgnore
        private UUID technicalId;

        @NotBlank
        private String name;
        private String city;
        private String coach;
    }

    @PostMapping(path = "/teams", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addTeam(@RequestBody @Valid Team team) {
        logger.info("Adding Team: {}", team.getName());
        return entityService.addItem(TEAM_ENTITY_NAME, ENTITY_VERSION, team);
    }

    @GetMapping(path = "/teams/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Team> getTeam(@PathVariable UUID id) {
        logger.info("Getting Team with id: {}", id);
        return entityService.getItem(TEAM_ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> objectMapper.convertValue(objectNode, Team.class));
    }

    @GetMapping(path = "/teams", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<List<Team>> getAllTeams() {
        logger.info("Getting all Teams");
        return entityService.getItems(TEAM_ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Team> teams = new ArrayList<>();
                    arrayNode.forEach(node -> teams.add(objectMapper.convertValue(node, Team.class)));
                    return teams;
                });
    }

    @PutMapping(path = "/teams/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> updateTeam(@PathVariable UUID id, @RequestBody @Valid Team team) {
        logger.info("Updating Team with id: {}", id);
        return entityService.updateItem(TEAM_ENTITY_NAME, ENTITY_VERSION, id, team);
    }

    @DeleteMapping(path = "/teams/{id}")
    public CompletableFuture<UUID> deleteTeam(@PathVariable UUID id) {
        logger.info("Deleting Team with id: {}", id);
        return entityService.deleteItem(TEAM_ENTITY_NAME, ENTITY_VERSION, id);
    }

    // Match entity endpoints
    private static final String MATCH_ENTITY_NAME = "Match";

    @Data
    public static class Match {
        @JsonIgnore
        private UUID technicalId;

        @NotBlank
        private String homeTeamId;
        @NotBlank
        private String awayTeamId;
        private String date;
        private Integer homeScore;
        private Integer awayScore;
    }

    @PostMapping(path = "/matches", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addMatch(@RequestBody @Valid Match match) {
        logger.info("Adding Match: homeTeamId={}, awayTeamId={}, date={}", match.getHomeTeamId(), match.getAwayTeamId(), match.getDate());
        return entityService.addItem(MATCH_ENTITY_NAME, ENTITY_VERSION, match);
    }

    @GetMapping(path = "/matches/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Match> getMatch(@PathVariable UUID id) {
        logger.info("Getting Match with id: {}", id);
        return entityService.getItem(MATCH_ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> objectMapper.convertValue(objectNode, Match.class));
    }

    @GetMapping(path = "/matches", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<List<Match>> getAllMatches() {
        logger.info("Getting all Matches");
        return entityService.getItems(MATCH_ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Match> matches = new ArrayList<>();
                    arrayNode.forEach(node -> matches.add(objectMapper.convertValue(node, Match.class)));
                    return matches;
                });
    }

    @PutMapping(path = "/matches/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> updateMatch(@PathVariable UUID id, @RequestBody @Valid Match match) {
        logger.info("Updating Match with id: {}", id);
        return entityService.updateItem(MATCH_ENTITY_NAME, ENTITY_VERSION, id, match);
    }

    @DeleteMapping(path = "/matches/{id}")
    public CompletableFuture<UUID> deleteMatch(@PathVariable UUID id) {
        logger.info("Deleting Match with id: {}", id);
        return entityService.deleteItem(MATCH_ENTITY_NAME, ENTITY_VERSION, id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}
