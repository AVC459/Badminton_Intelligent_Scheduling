package com.ape.badminton.common;

import lombok.Data;

/**
 * 统一响应体
 * 约定：code = 0 成功；非 0 为业务错误码
 * 错误码：40001 参数错误 / 40002 业务冲突 / 40100 未登录 / 40300 无权限 / 40400 不存在 / 50000 系统异常
 */
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
