-- 镜头历史按用户、项目、镜头和游标查询；恢复扫描按执行代次检查已有模型调用。
CREATE INDEX idx_generation_shot_history ON manga_generation_job (user_id, project_id, shot_id, id);
CREATE INDEX idx_model_call_step_epoch ON manga_model_call_ledger (step_id, owner_epoch, id);
CREATE INDEX idx_run_manual_history ON manga_creative_run (user_id, trigger_source, id);
