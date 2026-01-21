# User Management - Auth0 Sync

Purpose

This service will synchronize user accounts from Auth0 into the local Cyoda-managed user store. It will import Auth0 metadata, identity provider attributes, and maintain local-only fields for application usage.

Sync Trigger

- Manual sync (on-demand via UI or CLI)

Merge Policy

- Auth0 authoritative: When a user exists both in Auth0 and locally, the Auth0 values will overwrite local values for shared fields. Local-only fields (e.g., environments, present_in_auth0) will be preserved or updated as specified below.

Auth0 Metadata

- Optional fields that may be present in the Auth0 user_metadata:
  - caas_user_id: string
  - caas_cyoda_employee: boolean
  - caas_tier: string (e.g., "unlimited")

Local-only Fields

- present_in_auth0: boolean — true if present in Auth0 after the most recent sync
- environments: array of environment objects with fields:
  - name: string
  - last_access_time: OffsetDateTime
  - marked_for_deletion: boolean

Behavior

- Pull users from Auth0 via the Auth0 Management API. Include all user profile fields, metadata (user_metadata and app_metadata), and identity provider attributes.
- If a user exists in Auth0 but not locally: create a new local user record with Auth0 values and set present_in_auth0 = true.
- If a user exists locally but not in Auth0: set present_in_auth0 = false and preserve local environments.
- For users present in both places: merge according to Auth0 authoritative policy. Local-only fields (environments) should be preserved unless explicitly overwritten by the merge rules.
- Keep a sync audit log with timestamps and counts (created, updated, missing) after each manual run.

Security & Configuration

- Auth0 credentials (domain, client id, client secret) will be provided via Cyoda properties/environment variables and managed by the Environment agent during deployment.
- All secrets must not be committed to the repository. Use placeholders in configuration templates.

Deliverables

- Functional requirements saved to the repository at: src/main/resources/functional_requirements/user-management-auth0.md
- Next steps: generate entities (User, Environment) and a Workflow to perform the manual sync
