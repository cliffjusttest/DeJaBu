ALTER TABLE users
    ADD COLUMN has_character BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN appearance VARCHAR(32);

UPDATE users
SET has_character = TRUE
WHERE element IS NOT NULL;

UPDATE users
SET appearance = 'ADVENTURER'
WHERE has_character = TRUE AND appearance IS NULL;

ALTER TABLE users
    DROP CONSTRAINT chk_users_element;

ALTER TABLE users
    ALTER COLUMN element DROP NOT NULL;

ALTER TABLE users
    ALTER COLUMN element DROP DEFAULT;

ALTER TABLE users
    ADD CONSTRAINT chk_users_element
        CHECK (element IS NULL OR element IN ('FIRE', 'WIND', 'EARTH', 'THUNDER', 'WATER'));

ALTER TABLE users
    ADD CONSTRAINT chk_users_appearance
        CHECK (appearance IS NULL OR appearance IN ('ADVENTURER', 'WARRIOR', 'MAGE', 'RANGER', 'ROGUE'));
