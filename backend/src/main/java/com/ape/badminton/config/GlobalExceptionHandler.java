package com.ape.badminton.config;

import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一转换为 ApiResponse，避免异常堆栈直接暴露给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBiz(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 请求地址没有匹配到任何接口。
     *
     * 最常见的原因是调用方漏传路径参数，例如 `/api/tournament/getById/` + 空ID
     * 变成 `/api/tournament/getById`。这类问题若落进下面的兜底分支，会被包装成
     * 「服务繁忙，请稍后再试」——把「前端参数缺失」伪装成「后端挂了」，
     * 排查时极难定位。因此单独识别并明确回话。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<?> handleNoResource(NoResourceFoundException e) {
        log.warn("请求地址不存在：{}", e.getMessage());
        return ApiResponse.error(40404, "接口不存在，请检查请求地址（常见原因：缺少路径参数）");
    }

    /** 请求方法不匹配（如用 GET 调了 POST 接口） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return ApiResponse.error(40500, "请求方式不正确：" + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(50000, "服务繁忙，请稍后再试");
    }
}
