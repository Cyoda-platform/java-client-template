# User Management — Functional Requirements

## Overview

A Java-based user management application that synchronizes user data from the Auth0 API into a local store. The system will import metadata and Identity Provider attributes and manage additional local-only fields to track presence in Auth0 and environment-specific data.

## Core Requirements

1. Auth0 Integration
   - Pull users from the Auth0 Management API using configurable credentials provided via properties or environment variables.
   - Retrieve user metadata (including optional fields: caas_user_id, caas_cyoda_employee, caas_tier) and Identity Provider attributes.

2. Local Data Model
   - User entity must contain all Auth0 fields of interest plus the following local fields:
     - present_in_auth0: boolean
     - environments: list of environment objects:
       - name: string
       - last_access_time: OffsetDateTime
       - marked_for_deletion: boolean

3. Merge Semantics
   - On sync, mark local users not returned by Auth0 as present_in_auth0 = false.
   - If a user exists in Auth0 but not locally, add the user and set present_in_auth0 = true.
   - Preserve local-only fields (environments and marked_for_deletion) when merging for users present both locally and in Auth0.

4. Sync Modes
   - Manual on-demand sync triggered via UI or CLI (per user request). This is the chosen default.

5. Configuration & Security
   - Auth0 credentials must be provided via properties (application.properties or .env) and should be managed by the environment agent for secrets provisioning.

6. APIs
   - Endpoint to trigger sync: POST /sync
   - Endpoint to list users: GET /users
   - Endpoint to update environment flags for a user: PATCH /users/{id}/environments

7. Error Handling & Retries
   - Graceful handling of partial failures from Auth0 with clear logging.
   - Retry policy configurable via properties (attempts, backoff strategy).

## Acceptance Criteria

- Manual sync via API or CLI imports users from Auth0, creating missing local users and marking absent ones.
- Local environments metadata is retained through merges.
- Unit tests cover merge logic and API endpoints.

## Example User Flows

1. Operator triggers POST /sync. System fetches users from Auth0, merges into the local DB, and returns a summary: users_added, users_marked_absent, users_updated.
2. Admin lists users: GET /users returns combined data including present_in_auth0 and environments.
3. Admin patches environment flags: PATCH /users/{id}/environments to mark an environment for deletion.

