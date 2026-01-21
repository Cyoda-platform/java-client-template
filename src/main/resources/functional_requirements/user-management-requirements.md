# User Management - Auth0 Sync

Goal

Create a Java-based user-management application that:

- Pulls users from the Auth0 Management API using credentials supplied via properties (host, client id, client secret, audience).
- Reads Auth0 user metadata including these optional fields:

```
{
  "caas_user_id": "176a8b4d2b7f4da5a4a94ebb44531287",
  "caas_cyoda_employee": true,
  "caas_tier": "unlimited"
}
```

- Stores and manages additional local fields per user:
  - `present_in_auth0`: boolean
  - `environments`: array of objects with:
    - `name`: string
    - `last_access_time`: OffsetDateTime
    - `marked_for_deletion`: boolean

- Supports manual, on-demand synchronization triggered from UI or CLI.
- Merges Auth0 data with local store:
  - If a user exists locally but not in Auth0, set `present_in_auth0` to false.
  - If a user exists in Auth0 but not locally, add them to local store (with `present_in_auth0` true and empty environments).

Non-functional

- Configuration via properties (`application.yaml` or `.env`): Auth0 host, client id, client secret, audience, polling interval optionally.
- Use GitOps: all configuration and artifacts stored in repo.
- Provide unit tests for merge logic.

Deliverables

- Entity definitions for User and Environment
- A workflow or service for manual sync via REST endpoint and CLI command
- Persistence using embedded DB (H2) for local storage
- README with run instructions

