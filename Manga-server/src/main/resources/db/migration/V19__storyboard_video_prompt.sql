ALTER TABLE manga_storyboard_shot
    ADD COLUMN video_prompt TEXT NULL COMMENT '镜头自定义视频提示词，NULL 表示沿用画面内容或标题，空字符串表示清空';
