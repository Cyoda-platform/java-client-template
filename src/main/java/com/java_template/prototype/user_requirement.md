# Complete Requirement Specification

## Service Overview
- Build a service in **Java**.
- The service stores **Hacker News items**.
- Items are stored in the **JSON format** of the **Firebase HN API**.

## Item Storage and Validation
- When saving an item:
  - Validate that the fields **`id`** and **`type`** are present.
- Each item must be:
  - Enriched with an **import timestamp**.
    - The timestamp is **kept separate** from the original JSON.
  - Assigned a **state**:
    - **VALID** if the required fields (`id` and `type`) are present.
    - **INVALID** otherwise.

## Retrieval Functionality
- The service must allow retrieval of an item by its **`id`**.
- Retrieval must return:
  - The **original JSON** of the item.
  - Its **state** (VALID or INVALID).
  - Its **import timestamp**.

## Additional Notes
- The JSON format must strictly adhere to the **Firebase Hacker News API** format.
- Validation logic must be applied at save time to enforce data integrity.

---

⚠ **Clarification Needed**  
- Should the import timestamp be stored as a specific format (e.g., ISO 8601, Unix epoch milliseconds)?  
- Should the service expose a REST API or another interface for saving and retrieving items?  
- Is there a preferred persistence mechanism (e.g., in-memory, file system, database)?  
- Should the service handle updates to existing items or only new inserts?