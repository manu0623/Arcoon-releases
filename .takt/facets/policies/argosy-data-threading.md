# Argosy data and threading policy

Select for changes to entities, DAOs, migrations, repositories, network calls, or code on launch,
session, sync or frame paths. Sources: AGENTS.md, CONTRIBUTING.md AS-4, code-quality skill.
All rules are blocking.

## Database

- A schema change ships all four parts: a migration in `data/local/migrations/Migrations.kt`, an
  append to `MigrationRegistry.ALL`, a version bump in `ALauncherDatabase`, and the exported schema
  JSON under `app/schemas/`. Any missing part is REJECT, for every entity.
- A DAO, entity or foreign-key change accounts for every entity with a foreign key to the table,
  creates before referencing and deletes after dereferencing.
- A query that reads per-user rows takes the owner id as a required input.

## Main thread

- File, network, database and blocking native work (`GLRetroView` serialize and destroy calls) runs
  on `Dispatchers.IO`. Such work reachable from the main thread is REJECT.
- A progress overlay or spinner shown over work that blocks the main thread is REJECT.

## Hot paths

- New network calls, database writes or blocking work on app launch, game launch, session start or
  end, reconcile, the sync drain, or a frame or render path must be declared in the PR's Hot paths
  section with its cost and why it is acceptable. Undeclared is REJECT.

## Network models

- A RomM API or Moshi model change keeps device-aware variants and version gates, and tolerates
  fields the server omits.
