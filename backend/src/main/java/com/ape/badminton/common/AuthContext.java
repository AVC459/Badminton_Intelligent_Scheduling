package com.ape.badminton.common;

import com.ape.badminton.entity.User;

/**
 * 当前登录用户上下文
 *
 * 由 {@code TokenInterceptor} 在请求进入时写入、请求结束时清理。
 * 系统只有「比赛发起者」一种身份，因此这里不再提供角色判断能力，
 * 只负责提供当前发起者身份，用于「我的选手库 / 我创建的赛事」的数据隔离。
 */
public final class AuthContext {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static String userId() {
        User u = HOLDER.get();
        return u == null ? null : u.getUser_id();
    }

    public static String username() {
        User u = HOLDER.get();
        return u == null ? null : u.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
