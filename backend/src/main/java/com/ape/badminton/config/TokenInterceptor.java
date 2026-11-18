package com.ape.badminton.config;

import com.ape.badminton.common.AuthContext;
import com.ape.badminton.entity.User;
import com.ape.badminton.utils.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token 登录态拦截器
 *
 * 校验 Authorization: Bearer &lt;token&gt;，未登录返回 401；
 * 登录有效时把当前用户写入 {@link AuthContext}，请求结束后清理，避免线程复用导致身份串号。
 */
public class TokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }

        User user = TokenUtil.getUser(token);
        if (user != null) {
            AuthContext.set(user);
            return true;
        }

        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40100,\"message\":\"未登录或登录已过期\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
