ALTER TABLE users
    ADD COLUMN exp INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT chk_users_exp
        CHECK (exp >= 0);
