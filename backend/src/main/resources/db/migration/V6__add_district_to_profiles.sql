-- District retrofit, continued (see V5__add_districts.sql): profiles gain the same third
-- location tier. Plain nullable VARCHAR, same posture as the existing state/city columns --
-- completeness is enforced by ProfileCompletionService, not a database constraint.
ALTER TABLE profiles ADD COLUMN district VARCHAR;
