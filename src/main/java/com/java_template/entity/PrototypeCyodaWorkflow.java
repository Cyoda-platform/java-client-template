Certainly! Moving async logic and processing inside the workflow functions (`processSubscriber`, `processGame`) is a great design improvement. This encapsulates entity-specific business logic close to the persistence layer, makes controllers thinner, and improves maintainability.

---

### What will be moved?

1. **Subscriber workflow (`processSubscriber`)**  
   - Currently, subscription controller only adds subscriber. No extra async logic to move here.  
   - But we can add any validations, normalization (`email` to lowercase), or enrichment here.

2. **Game workflow (`processGame`)**  
   - The biggest async logic is in the `fetchStoreNotify` method that fetches games from external API, deletes old games for that date, adds new games, then notifies subscribers.  
   - This entire process will be moved into `processGame`.  
   - Since `processGame` is applied per game entity just before persistence, and it receives the entity as an `ObjectNode`, we **cannot** call add/update/delete for the current entity model (`game`) inside `processGame` to avoid infinite recursion.  
   - However, the requirement says we **can** get/add entities of **different** entityModels inside the workflow.  
   - Therefore, we will create a new entity model, e.g. `"externalGame"` or `"rawGame"` for the raw fetched data and persist it there from inside `processGame`.  
   - Alternatively, since the original `fetchStoreNotify` fetches all games for a date and deletes old ones, this bulk operation cannot be done per individual game entity easily inside `processGame`. Instead, we can move the entire fetch + notify logic into a new workflow function bound to a different entity model, e.g. `fetchRequest` or `scoreFetchRequest`.  
   - Then, the controller will just add a `fetchRequest` entity with the date, and the workflow for `fetchRequest` entity will perform the full fetch/delete/add/notify asynchronously.  
   - This approach fits the requirement fully and moves all async logic out of the controller.

---

### Adjusted Approach

- Create a new entity model `scoreFetchRequest` with a `processScoreFetchRequest` workflow function.  
- Controller will just add a `scoreFetchRequest` entity with the date and empty other fields.  
- `processScoreFetchRequest` will perform:  
  - fetch external scores from API  
  - delete old `game` entities for that date  
  - add new `game` entities with workflow `processGame` (which can just normalize/validate games)  
  - notify subscribers  
- `processGame` can contain light validation or normalization of game entity.  
- `processSubscriber` normalizes email.

---

### Code snippet demonstrating this approach

```java
// Add new entity model ScoreFetchRequest with workflow processScoreFetchRequest

// Controller method changes:
/*
@PostMapping("/fetch-scores")
public CompletableFuture<ResponseEntity<FetchScoresResponse>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
    logger.info("Received fetch scores request for date: {}", request.getDate());
    // Add scoreFetchRequest entity - triggers processScoreFetchRequest workflow async
    Map<String, Object> fetchRequestEntity = new HashMap<>();
    fetchRequestEntity.put("date", request.getDate());
    return entityService.addItem("scoreFetchRequest", ENTITY_VERSION, fetchRequestEntity, processScoreFetchRequest)
            .thenApply(id -> ResponseEntity.ok(new FetchScoresResponse("Scores fetching started", request.getDate(), 0)));
}
*/

// Workflow function for scoreFetchRequest
private Function<Object, Object> processScoreFetchRequest = entity -> {
    ObjectNode entityNode = (ObjectNode) entity;
    String dateStr = entityNode.get("date").asText();

    try {
        // Fetch external game scores
        String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", dateStr);
        String jsonResponse = restTemplate.getForObject(new URI(url), String.class);
        JsonNode root = objectMapper.readTree(jsonResponse);

        if (!root.isArray()) {
            logger.error("Unexpected response format during score fetch");
            return entity;
        }

        // Delete old games for the date
        Condition condition = Condition.of("$.date", "EQUALS", dateStr);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        CompletableFuture<Void> deleteOldGamesFuture = entityService.getItemsByCondition("game", ENTITY_VERSION, searchCondition)
                .thenCompose(oldGames -> {
                    List<CompletableFuture<UUID>> deletes = new ArrayList<>();
                    for (JsonNode oldGame : oldGames) {
                        UUID techId = UUID.fromString(oldGame.get("technicalId").asText());
                        deletes.add(entityService.deleteItem("game", ENTITY_VERSION, techId));
                    }
                    return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0]));
                });

        deleteOldGamesFuture.join();

        // Add new games
        List<CompletableFuture<UUID>> addGameFutures = new ArrayList<>();
        for (JsonNode gameNode : root) {
            Map<String, Object> gameMap = new HashMap<>();
            gameMap.put("gameId", gameNode.path("GameID").asText());
            gameMap.put("date", dateStr);
            gameMap.put("homeTeam", gameNode.path("HomeTeam").asText());
            gameMap.put("awayTeam", gameNode.path("AwayTeam").asText());
            if (gameNode.hasNonNull("HomeTeamScore")) {
                gameMap.put("homeScore", gameNode.get("HomeTeamScore").asInt());
            }
            if (gameNode.hasNonNull("AwayTeamScore")) {
                gameMap.put("awayScore", gameNode.get("AwayTeamScore").asInt());
            }
            addGameFutures.add(entityService.addItem("game", ENTITY_VERSION, gameMap, processGame));
        }
        CompletableFuture.allOf(addGameFutures.toArray(new CompletableFuture[0])).join();

        // Notify subscribers
        entityService.getItems("subscriber", ENTITY_VERSION).thenAccept(subs -> {
            if (subs.isEmpty()) return;
            StringBuilder content = new StringBuilder("Daily NBA Scores for ").append(dateStr).append(":\n\n");
            for (JsonNode g : root) {
                content.append(g.path("AwayTeam").asText())
                        .append(" @ ").append(g.path("HomeTeam").asText())
                        .append(": ").append(g.path("AwayTeamScore").isInt() ? g.path("AwayTeamScore").asInt() : "N/A")
                        .append(" - ").append(g.path("HomeTeamScore").isInt() ? g.path("HomeTeamScore").asInt() : "N/A")
                        .append("\n");
            }
            for (JsonNode sub : subs) {
                String email = sub.get("email").asText();
                logger.info("Sending email to {}:\n{}", email, content);
            }
        }).join();

    } catch (Exception e) {
        logger.error("Error in processScoreFetchRequest workflow", e);
    }
    return entity;
};

// Updated processGame workflow function (light normalization)
private Function<Object, Object> processGame = entity -> {
    ObjectNode node = (ObjectNode) entity;
    // Normalize scores to Integer or null
    if (!node.has("homeScore") || node.get("homeScore").isNull()) {
        node.putNull("homeScore");
    }
    if (!node.has("awayScore") || node.get("awayScore").isNull()) {
        node.putNull("awayScore");
    }
    return node;
};

// Updated processSubscriber workflow function (email normalization)
private Function<Object, Object> processSubscriber = entity -> {
    ObjectNode node = (ObjectNode) entity;
    if (node.has("email") && node.get("email").isTextual()) {
        String normalizedEmail = node.get("email").asText().toLowerCase(Locale.ROOT);
        node.put("email", normalizedEmail);
    }
    return node;
};
```

---

### Summary of the full change:

- **New entity model** `scoreFetchRequest` which represents a request to fetch and refresh scores for a particular date.
- **Controller** on `/fetch-scores` just adds a `scoreFetchRequest` entity with the date, using workflow `processScoreFetchRequest`.
- **`processScoreFetchRequest` Workflow** performs the entire async fetch, delete old `game`s, add new `game`s with `processGame` workflow, and notify subscribers.
- **`processGame` Workflow** handles game entity normalization.
- **`processSubscriber` Workflow** normalizes subscriber emails.
- **Controller methods significantly simpler,** with no async logic other than adding entities with workflows.

---

If you want, I can provide full updated controller code reflecting these changes, or just the workflow functions and controller method. Just let me know.