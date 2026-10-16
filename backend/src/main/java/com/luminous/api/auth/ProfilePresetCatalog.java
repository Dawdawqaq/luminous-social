package com.luminous.api.auth;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;

/** 个人资料内置图片目录，静态文件由前端提供，选择结果复用已有图片地址字段。 */
public final class ProfilePresetCatalog {
    public record Preset(String id, String name, String url) {}
    public record Catalog(List<Preset> avatars, List<Preset> banners) {}

    private static final Catalog CATALOG = new Catalog(List.of(
            new Preset("pearl", "珍珠耳环", "/profile-presets/avatars/pearl.webp"),
            new Preset("david", "大卫雕塑", "/profile-presets/avatars/david.webp"),
            new Preset("phantom-thief", "怪盗基德", "/profile-presets/avatars/phantom-thief.webp"),
            new Preset("scream-starry", "呐喊与星月夜", "/profile-presets/avatars/scream-starry.webp"),
            new Preset("godfather", "教父", "/profile-presets/avatars/godfather.webp"),
            new Preset("pharaoh", "法老", "/profile-presets/avatars/pharaoh.webp"),
            new Preset("totoro", "龙猫", "/profile-presets/avatars/totoro.webp"),
            new Preset("little-prince", "小王子", "/profile-presets/avatars/little-prince.webp"),
            new Preset("liberty", "自由女神", "/profile-presets/avatars/liberty.webp")
    ), List.of(
            new Preset("blue-hour", "蓝色时刻", "/profile-presets/banners/blue-hour.svg"),
            new Preset("lemon-paper", "柠檬画纸", "/profile-presets/banners/lemon-paper.svg"),
            new Preset("star-orbit", "星星轨道", "/profile-presets/banners/star-orbit.svg"),
            new Preset("comic-panels", "日常分镜", "/profile-presets/banners/comic-panels.svg"),
            new Preset("blue-wave", "蓝色浪潮", "/profile-presets/banners/blue-wave.svg"),
            new Preset("little-spark", "一点星光", "/profile-presets/banners/little-spark.svg")
    ));

    private ProfilePresetCatalog() {}

    public static Catalog catalog() {
        return CATALOG;
    }

    /** 两次独立抽样，注册后持久化，刷新页面不会改变预设。 */
    public static void assignRandom(User user) {
        assign(user, null);
    }

    /** 只接受目录内的预设标识；旧客户端未指定时仍可正常注册并获得随机头像。 */
    public static void assign(User user, String avatarPresetId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Preset avatar = avatarPresetId == null
                ? CATALOG.avatars().get(random.nextInt(CATALOG.avatars().size()))
                : CATALOG.avatars().stream().filter(preset -> preset.id().equals(avatarPresetId)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR_PRESET", "该头像预设不可用，请重新选择"));
        user.setAvatarUrl(avatar.url());
        user.setBannerUrl(CATALOG.banners().get(random.nextInt(CATALOG.banners().size())).url());
    }
}
