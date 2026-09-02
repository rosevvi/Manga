package com.manga.config;

import com.manga.common.constant.ProjectConstants;
import com.manga.dto.ArtStylePresetResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册项目可选画风预设，用于创作提示词和前端选择器。 */
public final class ArtStylePresets {

    /** 按声明顺序保存的内置画风预设。 */
    private static final Map<String, ArtStylePresetResponse> PRESETS = new LinkedHashMap<>();

    static {
        register(new ArtStylePresetResponse(
                "cartoon_3d",
                "卡通3D",
                "高品质 3D 卡通动画风格，角色比例夸张有表现力，色彩明快，适合轻快、亲和力强的漫剧短片。",
                "High quality 3D cartoon animation style, expressive stylized characters, soft global illumination, vibrant warm colors, cinematic composition.",
                ProjectConstants.ART_STYLE_IMAGE_PATH_PREFIX + "cartoon_3d.jpg"));
        register(new ArtStylePresetResponse(
                "cg",
                "CG动画",
                "次世代 CG 动画质感，强调精细建模、戏剧性光影和粒子特效，适合奇幻、科幻和大场面叙事。",
                "Next-generation CG animation style, high detail 3D models, PBR materials, dramatic lighting, cinematic ray tracing, epic visual effects.",
                ProjectConstants.ART_STYLE_IMAGE_PATH_PREFIX + "cg.jpg"));
        register(new ArtStylePresetResponse(
                "realistic",
                "写实",
                "电影级写实摄影风格，真实光影、浅景深和胶片质感，适合现实题材、悬疑和情绪化叙事。",
                "Cinematic realistic photography, 35mm lens, shallow depth of field, professional lighting, film grain, high realism, detailed textures.",
                ProjectConstants.ART_STYLE_IMAGE_PATH_PREFIX + "realistic.jpg"));
        register(new ArtStylePresetResponse(
                "anime_jp",
                "日漫",
                "高品质 2D 日式动画风格，线稿清晰、赛璐璐上色、光影唯美，适合青春、冒险和幻想题材。",
                "High quality Japanese anime style, cel shading, clean line art, vivid colors, delicate 2D backgrounds, atmospheric lighting.",
                ProjectConstants.ART_STYLE_IMAGE_PATH_PREFIX + "anime_jp.jpg"));
        register(new ArtStylePresetResponse(
                "anime_cn",
                "国漫",
                "新国风动画风格，融合水墨、工笔和现代数字插画，适合东方奇幻、武侠和古风故事。",
                "Modern Chinese animation style, ink wash painting, elegant linework, oriental fantasy mood, traditional color palette, premium 2D/3D hybrid look.",
                ProjectConstants.ART_STYLE_IMAGE_PATH_PREFIX + "anime_cn.jpg"));
        register(new ArtStylePresetResponse(
                "comic_us",
                "美漫",
                "现代美式漫画风格，粗犷轮廓、高对比色彩和强烈透视，适合动作、超级英雄和潮流短片。",
                "Modern American comic style, bold ink outlines, high contrast pop colors, dynamic perspective, halftone texture, stylized action framing.",
                ProjectConstants.ART_STYLE_IMAGE_PATH_PREFIX + "comic_us.jpg"));
    }

    /** 按唯一标识查询画风预设。 */
    public static ArtStylePresetResponse getByKey(String key) {
        return PRESETS.get(key);
    }

    /** 返回全部画风预设。 */
    public static List<ArtStylePresetResponse> getAll() {
        return new ArrayList<>(PRESETS.values());
    }

    /** 注册画风预设并保持声明顺序。 */
    private static void register(ArtStylePresetResponse preset) {
        PRESETS.put(preset.key(), preset);
    }

    /** 禁止实例化画风预设工具类。 */
    private ArtStylePresets() {
    }
}
