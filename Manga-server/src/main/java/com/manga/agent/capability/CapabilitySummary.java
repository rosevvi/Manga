package com.manga.agent.capability;

import java.util.Objects;

/** 由领域工具显式生成的公开摘要，类型契约不允许夹带正文、参数和供应商响应。 */
public record CapabilitySummary(/** 输入资源摘要。 */ String inputSummary, /** 领域结果摘要。 */ String resultSummary) {
    /** 目录标题的公开摘要字符上限。 */
    private static final int MAX_TITLE_CHARS = 120;

    /** 章节工具仅公开章节标识与标题。 */
    public static CapabilitySummary chapter(long id, String title) { return new CapabilitySummary("章节 #" + id, title(title)); }
    /** 分镜读取仅公开查询范围与镜头数量。 */
    public static CapabilitySummary shots(Long chapterId, int count) {
        return new CapabilitySummary(Objects.isNull(chapterId) ? "当前项目" : "章节 #" + chapterId, count + " 个镜头");
    }
    /** 剧本目录只公开标题与章节数。 */
    public static CapabilitySummary script(String title, int count) { return new CapabilitySummary("项目剧本", title(title) + " · " + count + " 章"); }
    /** 项目读取只公开标识、名称和分镜数量。 */
    public static CapabilitySummary project(long id, String name, Integer count) {
        return new CapabilitySummary("项目 #" + id, title(name) + " · " + Objects.requireNonNullElse(count, 0) + " 个镜头");
    }
    /** 限制标题长度，避免大字段进入公开执行记录。 */
    private static String title(String value) { return Objects.isNull(value) ? "未命名" : value.substring(0, Math.min(value.length(), MAX_TITLE_CHARS)); }
}
