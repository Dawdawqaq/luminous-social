-- 评论最多保存一张已上传的图片；历史文字评论无需补充图片。
ALTER TABLE comments ADD COLUMN image_url VARCHAR(512) NULL AFTER content;
