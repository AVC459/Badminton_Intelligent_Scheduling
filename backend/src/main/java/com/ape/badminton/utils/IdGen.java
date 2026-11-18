package com.ape.badminton.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量 ID 生成器
 *
 * 规则：前缀 + 时间戳（36 进制）+ 自增序列，保证同一毫秒内批量插入也不重复。
 * 长度可控（≤ 40 字符），便于作为 VARCHAR(50) 主键。
 */
public final class IdGen {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private static final char[] DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private IdGen() {
    }

    public static String next(String prefix) {
        long now = System.currentTimeMillis();
        long seq = SEQ.incrementAndGet() & 0xFFFF;
        return prefix + "_" + toBase36(now) + toBase36(seq);
    }

    private static String toBase36(long v) {
        if (v == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (v > 0) {
            sb.append(DIGITS[(int) (v % 36)]);
            v /= 36;
        }
        return sb.reverse().toString();
    }

    /** 场次ID前缀，长度受控，避免超出数据库字段长度 */
    public static String matchPrefix(String tournamentId) {
        String t = tournamentId == null ? "t" : tournamentId.replaceAll("[^A-Za-z0-9]", "");
        if (t.length() > 14) {
            t = t.substring(t.length() - 14);
        }
        return "m" + t;
    }
}
