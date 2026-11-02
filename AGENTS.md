# Motion Care working rules

- Keep work limited to the authorized scaffold or feature scope.
- Follow the module graph and rationale in docs/architecture.md. Do not split by developer name.
- UI uses screen-level ViewModels and immutable StateFlow UI state. No shared global busy flag for unrelated screens.
- Features use TripRepository; they never access database, session, sample, or vehicle implementations.
- Data implementations are internal. Only the repository and composition factory are public.
- Features must not depend on other features. App owns navigation and dependency wiring.
- core:model stays Kotlin/JVM only. core:ui contains reusable presentation components only.
- Changes to public API, build configuration, schema, navigation or ownership require updating the corresponding docs and coordinating affected owners.
- Preserve the existing sample database and use explicit migrations for schema changes.
- Synthetic data remains labeled. No fabricated medical thresholds, capabilities or fallback real measurements.
- No vehicle SDK, location, microphone, network permissions or keys without feature scope.
- Never add absolute personal paths, SDK binaries, AVDs, local.properties, caches or credentials to Git.
- Run python3 scripts/dev.py verify; run UI tests for navigation or lifecycle changes.
- Use project-managed test fixtures/devices. Report untested platform assumptions plainly.
- Comments/identifiers in English; user-facing docs and UI in Korean.
