package com.ape.badminton.dto;

import com.ape.badminton.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功返回：token + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginResult {
    private String token;
    private User user;
}
