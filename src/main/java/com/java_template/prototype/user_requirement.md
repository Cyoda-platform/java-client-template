# Requirement (as provided)

- Build a service in Java that stores Hacker News items in the JSON format of the Firebase HN API.
- When saving an item, it must validate that the fields "id" and "type" are present.
- It must enrich the item with an "importTimestamp".
- The service must also allow retrieval of an item by its id, returning the original JSON.