package com.ape.badminton.common;

/**
 * 查询参数清洗
 *
 * 前端（微信小程序）在把 JS 对象序列化成 query string 时，会把值为
 * {@code undefined} 的字段原样写成 {@code status=undefined}。后端若把它当成
 * 真实筛选条件，就会查出 0 条记录 —— 表现为「赛事明明创建了，列表却是空的」。
 *
 * 这里统一把 {@code undefined / null / 空白} 视为「未传该参数」，
 * 使接口对客户端的序列化差异免疫。
 */
public final class QueryParams {

    private QueryParams() {
    }

    /** 清洗文本型参数，未传时返回 null */
    public static String text(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()
                || "undefined".equalsIgnoreCase(s)
                || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    /** 清洗并解析布尔型参数，未传或无法识别时返回 null */
    public static Boolean bool(String v) {
        String s = text(v);
        if (s == null) return null;
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return Boolean.FALSE;
        return null;
    }

    /** 清洗并解析整型参数，未传或无法识别时返回 null */
    public static Integer integer(String v) {
        String s = text(v);
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
