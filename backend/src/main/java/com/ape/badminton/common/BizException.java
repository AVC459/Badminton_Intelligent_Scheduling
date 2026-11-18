package com.ape.badminton.common;

import lombok.Getter;

/**
 * 业务异常：在 Service/Controller 中抛出，由全局异常处理器统一转为 ApiResponse
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(40000, message);
    }
}
