ALTER TABLE users
    ADD COLUMN element VARCHAR(16) NOT NULL DEFAULT 'FIRE';

ALTER TABLE users
    ADD CONSTRAINT chk_users_element
        CHECK (element IN ('FIRE', 'WIND', 'EARTH', 'THUNDER', 'WATER'));
