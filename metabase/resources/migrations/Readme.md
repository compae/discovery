Since version 0.45 Metabase has split its migration in two files so that the DB initialization does not
take too long for fresh installs.

They have a `001_update_migrations.yaml` file whose "starting point"
is the database schema associated with the last migration of metabase 0.44, that they define in a sql file,
which is used for fresh installs an for migrations from version `>= 0.45`.

Then they have a `000_legacy_migrations.yaml` file with all the "old" migrations for cases where they
need to migrate from a metabase version `<= 0.44`.

For details: https://github.com/metabase/metabase/pull/34400

Thus, we need to add our subscription triggres to the "initial 0.44 state" of their sql script, and the
migrations adding the subscrition triggers to their `000_legacy_migrations.yaml` migrations files for
case where clients update from discovery versions `< 0.43.4-0.x` (this should not happen if they go
universe by universe but just in case)
