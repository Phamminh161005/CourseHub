# DiagramIo Project Guidelines

## Tech Stack

- Use Java 21 and Spring Boot for the backend REST API.
- Use PostgreSQL as the primary database.
- Use Spring Data JPA with Hibernate for persistence and Flyway for versioned database migrations.
- Use Spring Security for authentication and authorization. Implement short-lived JWT access tokens, refresh-token rotation/revocation, and BCrypt or Argon2 password hashing; do not store refresh tokens in plaintext.
- Use Jakarta Bean Validation (Hibernate Validator) for request validation and Jackson for JSON serialization/deserialization.
- Use JUnit 5, Mockito, and Spring Boot Test/MockMvc for automated tests. Use Testcontainers with PostgreSQL for tests that require database behavior when practical.
- Use springdoc-openapi for OpenAPI/Swagger documentation.
- Use Maven as the default build and dependency-management tool unless the repository already establishes Gradle.
- Package and run the service with Docker and Docker Compose. Keep local infrastructure configuration reproducible and do not commit secrets.

## Project Scope

- Build the backend REST API for an online UML and ERD diagram service.
- Keep the current phase focused on account management, diagram storage, access control, sharing, version history, and restoration.
- Do not implement frontend/canvas editing, real-time collaboration through WebSocket/CRDT/OT, third-party OAuth, or email password recovery.
- Treat diagram export to PNG, SVG, PDF, and JSON as a Phase 2 capability. Do not add export endpoints to the current phase unless the user explicitly requests a scope change.

## API Contract

- Use the `/api/v1` prefix for all application endpoints.
- Preserve the endpoint paths, HTTP methods, authentication requirements, and owner/editor/viewer permissions defined in `private/BTL KTPM.md`.
- Return JSON responses with clear, consistent error details and appropriate HTTP status codes.
- Public access is limited to public diagrams, valid sharing tokens, and the health endpoint. Protect all other resources with authentication.
- A newly created diagram must belong to the authenticated user, accept only `UML` or `ERD`, initialize its data as `{ "nodes": [], "edges": [] }`, and be private by default.

## Security and Authorization

- Never store plaintext passwords. Use the project's established password-hashing and token-handling libraries.
- Validate authentication and resource authorization at the service boundary; do not rely on frontend checks.
- Only owners may create, modify, or revoke sharing entries. Editors may update diagrams and create or restore versions but may not manage sharing.
- Generate cryptographically secure, unique sharing tokens and invalidate them immediately when revoked.
- Do not expose private diagram data, credentials, refresh tokens, or internal implementation details in public responses.
- Validate and constrain request bodies, including the maximum 2 MB diagram JSON payload, before persistence.

## Data Integrity and Concurrency

- Enforce optimistic locking for diagram updates using the client-supplied `version`.
- Reject stale updates with `409 Conflict` and leave the stored diagram unchanged.
- Treat saved diagram versions as immutable snapshots.
- Before restoring an old version, snapshot the current state, then copy the selected snapshot into the current diagram state atomically.
- Keep authorization checks, version checks, and related writes within the appropriate transaction boundaries.

## Implementation Practices

- Inspect the existing repository structure, framework conventions, schemas, and error-handling patterns before adding code.
- Reuse existing helpers, middleware, validators, and response types instead of duplicating them.
- Keep controllers/routes thin; place business rules in the appropriate service/domain layer.
- Validate enum values, identifiers, ownership, share permissions, token state, and JSON schema at the API boundary.
- Make migrations and schema changes explicit, reversible where the project supports it, and consistent with existing naming conventions.
- Update OpenAPI/Swagger documentation whenever an endpoint or request/response contract changes.
- Do not introduce unrelated refactors or silently change existing behavior.

## Testing and Verification

- Add or update tests for authentication, authorization, public/private access, sharing-token revocation, invalid payloads, optimistic-lock conflicts, immutable versions, and safe restoration.
- Include both successful and denied paths for owner, editor, viewer, unauthenticated, and public requests.
- Verify status codes and response shapes, not only whether a request succeeds.
- Run the smallest relevant formatter, linter, type checker, and test commands before considering a change complete.
- Use the repository's documented commands and configuration; do not invent replacement tooling without a clear need.

## Documentation and Delivery

- Keep API documentation and implementation behavior synchronized.
- Document intentional scope changes, especially any decision to move a Phase 2 feature into the current phase.
- Report validation commands and any unresolved environment limitations with the completed change.
