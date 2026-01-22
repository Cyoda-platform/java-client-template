# User Management - Sync-only

Goal

- Build a Java application that periodically syncs users from Auth0 and merges that data into a local, read-only store.

Scope

- Pull users from Auth0, including: user profile fields, user_metadata (optional caas_* fields), and identity provider attributes.
- Maintain a local user store with the following model:

```json
{
  "id": "string (local UUID)",
  "auth0_user_id": "string (auth0 user_id)",
  "email": "string",
  "name": "string",
  "user_metadata": {
    "caas_user_id": "string (optional)",
    "caas_cyoda_employee": "boolean (optional)",
    "caas_tier": "string (optional)"
  },
  "present_in_auth0": true,
  "environments": [
    {
      "name": "string",
      "last_access_time": "OffsetDateTime",
      "marked_for_deletion": "boolean"
    }
  ]
}
```

- Merge rules:
  - If a local user matches an Auth0 user by auth0_user_id, update local fields with the latest data from Auth0 and set present_in_auth0 = true.
  - If a local user is not present in Auth0 during sync, set present_in_auth0 = false but keep the record.
  - If an Auth0 user does not exist locally, create a new local user record with present_in_auth0 = true.

Functional Requirements

1. Periodic sync job (configurable interval) that:
   - Fetches users from Auth0 using credentials provided via application properties (domain, client id, client secret).
   - Includes identity provider attributes and user_metadata in the pulled data.
   - Applies merge rules to local store.
2. Local data storage: a read-only store from the perspective of the API (no CRUD endpoints), persisted to the application's data storage (e.g., embedded DB or configured data source).
3. Configuration:
   - Auth0 domain, client id, client secret provided as properties.
   - Sync interval and paging settings configurable.
4. Logging & observability:
   - Logs of sync runs, counts of created/updated/marked-offline users.

Non-functional Requirements

- App should be Java 11+ compatible.
- Prefer Spring Boot for scheduling and configuration.
- Handle Auth0 paging and rate limits gracefully.

Deliverables

- Java application scaffolded from Cyoda Java template with the sync job and persistence model.
- Functional requirements file saved in the repo at src/main/resources/functional_requirements/user-management-sync-only.md
