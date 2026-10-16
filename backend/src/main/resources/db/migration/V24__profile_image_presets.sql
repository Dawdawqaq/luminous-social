-- 仅补齐旧账号缺失的图片，已有预设、自定义头像及背景保持不变。
UPDATE users SET avatar_url = CASE MOD(id,9)
    WHEN 0 THEN '/profile-presets/avatars/pearl.webp'
    WHEN 1 THEN '/profile-presets/avatars/david.webp'
    WHEN 2 THEN '/profile-presets/avatars/phantom-thief.webp'
    WHEN 3 THEN '/profile-presets/avatars/scream-starry.webp'
    WHEN 4 THEN '/profile-presets/avatars/godfather.webp'
    WHEN 5 THEN '/profile-presets/avatars/pharaoh.webp'
    WHEN 6 THEN '/profile-presets/avatars/totoro.webp'
    WHEN 7 THEN '/profile-presets/avatars/little-prince.webp'
    ELSE '/profile-presets/avatars/liberty.webp'
END
WHERE avatar_url IS NULL OR TRIM(avatar_url)='';

-- 使用另一段用户 ID 作稳定分配，避免旧账号的头像与背景始终绑定同一组合。
UPDATE users SET banner_url = CASE MOD(FLOOR(id/9),6)
    WHEN 0 THEN '/profile-presets/banners/blue-hour.svg'
    WHEN 1 THEN '/profile-presets/banners/lemon-paper.svg'
    WHEN 2 THEN '/profile-presets/banners/star-orbit.svg'
    WHEN 3 THEN '/profile-presets/banners/comic-panels.svg'
    WHEN 4 THEN '/profile-presets/banners/blue-wave.svg'
    ELSE '/profile-presets/banners/little-spark.svg'
END
WHERE banner_url IS NULL OR TRIM(banner_url)='';
