CREATE INDEX idx_creative_run_manual_lookup
    ON manga_creative_run (user_id, project_id, trigger_source, id);
