-- Tracks whether a user's saved Groww credentials have been verified by a
-- real Test Connection call. Changing the API key/TOTP secret (or a failed
-- re-authentication attempt) flips connected back to FALSE - only an
-- explicit, successful Test Connection call sets it TRUE again.

ALTER TABLE groww_configuration ADD COLUMN connected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE groww_configuration ADD COLUMN last_connected_at DATETIME(6) NULL;
