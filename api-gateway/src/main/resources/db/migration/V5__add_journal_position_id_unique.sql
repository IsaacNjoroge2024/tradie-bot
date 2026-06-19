-- Prevent duplicate journal rows for the same position.
-- Partial index allows multiple NULL position_ids (manually created entries).
CREATE UNIQUE INDEX idx_journal_position_id_unique
    ON trade_journal (position_id)
    WHERE position_id IS NOT NULL;
