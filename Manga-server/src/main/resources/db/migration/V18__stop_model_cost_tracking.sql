-- 新调用仅记录状态与用量，历史金额继续留存以供离线核查。
ALTER TABLE manga_model_call_ledger
    MODIFY COLUMN reserved_usd DECIMAL(20,8) NULL COMMENT '历史规划预留 USD，新调用不写入',
    COMMENT = '模型调用状态和供应商报告的用量，不存提示词或凭据';
