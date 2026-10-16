package com.luminous.api.lumi;

import java.util.regex.Pattern;

/** 只把当前新评论中完整的 @Lumi 作为调用，引用和代码里的文字不触发。 */
public final class LumiIntent {
    private static final Pattern MENTION = Pattern.compile("(?i)(?<![A-Za-z0-9_@\\\\])@lumi(?![A-Za-z0-9_])");
    private static final Pattern WEB = Pattern.compile("(?iu)(联网|上网|网上|站外).{0,4}(搜|查|检索|核实|核查|核验)|(?:请|帮我|麻烦)联网|^联网[。！!？?]*$");
    private LumiIntent() {}
    public static String question(String content) {
        String plain = content.replaceAll("(?s)```.*?(?:```|\\z)", "").replaceAll("`[^`]*`", "").replaceAll("(?m)^\\s*>.*$", "");
        var matcher = MENTION.matcher(plain);
        if (!matcher.find()) return null;
        return matcher.replaceAll("").trim();
    }
    public static boolean requestsWeb(String question) {
        String positive = question.replaceAll("(?:不要|不必|别|无需|禁止)(?:再|去)?(?:联网|上网|网上搜索|站外搜索)", "");
        return WEB.matcher(positive).find();
    }
}
