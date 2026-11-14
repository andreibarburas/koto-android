# Room schemas

Room writes the generated schema JSON for each database version here, because
`exportSchema = true` on `KotoDatabase`.

**Commit these files.** They are what a future migration is written and tested against.
Without the JSON for version N, there is no way to verify that the migration from N to N+1
produces the right shape, and `MigrationTestHelper` cannot run at all.

Koto has no destructive fallback: the `pending` and `deletedLocally` rows hold edits made
offline that have never reached Nextcloud, and dropping the database would lose them with no
trace. Every schema change therefore needs a real migration in `KotoMigrations`.
