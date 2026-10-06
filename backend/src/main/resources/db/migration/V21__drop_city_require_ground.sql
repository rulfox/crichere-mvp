-- Design update #6 (docs/OPEN-ITEMS.md 6.1, decided 2026-10-06): location is State -> District only,
-- and every league has a ground.
--
-- City goes everywhere: LGD has no real city tier (V20 had to stitch one together from urban local
-- bodies and sub-districts), and the ground's map pin already gives "Nearest to me" a precise point.
-- The app is unreleased, so the columns are dropped rather than left nullable.
--
-- Ground becomes mandatory so every active league takes part in "Nearest to me". Leagues saved
-- without one are deleted (owner decision 2026-10-06); their awards, players, franchises, follows,
-- roles and bids go with them through the existing ON DELETE CASCADE foreign keys. The league keeps
-- its own state/district (not copied from the ground).
--
-- The ground FK switches from V8's ON DELETE SET NULL to RESTRICT: a ground can no longer silently
-- vanish from a league (there is no delete-ground endpoint today either).

DELETE FROM leagues WHERE ground_id IS NULL;

ALTER TABLE leagues DROP CONSTRAINT leagues_ground_id_fkey;
ALTER TABLE leagues ADD CONSTRAINT leagues_ground_id_fkey
    FOREIGN KEY (ground_id) REFERENCES grounds(id) ON DELETE RESTRICT;
ALTER TABLE leagues ALTER COLUMN ground_id SET NOT NULL;

ALTER TABLE profiles DROP COLUMN city;
ALTER TABLE leagues DROP COLUMN city;
ALTER TABLE grounds DROP COLUMN city;

DROP TABLE cities;
