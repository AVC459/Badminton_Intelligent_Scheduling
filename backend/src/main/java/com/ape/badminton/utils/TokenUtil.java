package com.ape.badminton.utils;

import com.ape.badminton.entity.User;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 工具类（轻量实现，零外部依赖）
 *
 * 说明：当前使用 UUID + 内存缓存，适合演示/中小规模场景。
 * 生产环境建议替换为 JWT（jjwt）+ Redis，并将 TOKEN_EXPIRE 迁移至 Redis 做分布式共享。
 */
public class TokenUtil {

    /** token -> 用户 */
    private static final Map<String, User> TOKEN_STORE = new ConcurrentHashMap<>();

    /** token -> 过期时间戳 */
    private static final Map<String, Long> TOKEN_EXPIRE = new ConcurrentHashMap<>();

    /** 有效期：7 天 */
    private static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

    public static String createToken(User user) {
        String token = "tk_" + UUID.randomUUID().toString().replace("-", "");
        TOKEN_STORE.put(token, user);
        TOKEN_EXPIRE.put(token, System.currentTimeMillis() + EXPIRE_MS);
        return token;
    }

    /**
     * 根据 token 获取用户；过期或不存在返回 null
     */
    public static User getUser(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Long expire = TOKEN_EXPIRE.get(token);
        if (expire == null) {
            return null;
        }
        if (System.currentTimeMillis() > expire) {
            remove(token);
            return null;
        }
        return TOKEN_STORE.get(token);
    }

    public static void remove(String token) {
        if (token == null) {
            return;
        }
        TOKEN_STORE.remove(token);
        TOKEN_EXPIRE.remove(token);
    }
}
