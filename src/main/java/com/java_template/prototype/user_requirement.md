# Complete Requirement Specification

## Service Overview
- Build a service in **Java**.
- The service stores **Hacker News items**.
- Items are stored in the **JSON format** of the **Firebase HN API**.

## Item Saving Requirements
- When saving an item:
  - Validate that the fields **`id`** and **`type`** are present.
  - Each item must be enriched with an **import timestamp**.
    - The import timestamp must be kept **separate from the original JSON**.
  - Each item must be assigned a **state**:
    - **VALID** if the required fields (`id` and `type`) are present.
    - **INVALID** if any of the required fields are missing.

## Item Retrieval Requirements
- The service must allow **retrieval of an item by its `id`**.
- When retrieving, the service must return:
  - The **original JSON** of the item.
  - The **state** (VALID or INVALID).
  - The **import timestamp**.