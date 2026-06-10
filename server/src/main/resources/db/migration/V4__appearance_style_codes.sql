ALTER TABLE users
    DROP CONSTRAINT chk_users_appearance;

UPDATE users SET appearance = 'STYLE_1' WHERE appearance = 'ADVENTURER';
UPDATE users SET appearance = 'STYLE_2' WHERE appearance = 'WARRIOR';
UPDATE users SET appearance = 'STYLE_3' WHERE appearance = 'MAGE';
UPDATE users SET appearance = 'STYLE_4' WHERE appearance = 'RANGER';
UPDATE users SET appearance = 'STYLE_5' WHERE appearance = 'ROGUE';

ALTER TABLE users
    ADD CONSTRAINT chk_users_appearance
        CHECK (appearance IS NULL OR appearance IN ('STYLE_1', 'STYLE_2', 'STYLE_3', 'STYLE_4', 'STYLE_5'));
