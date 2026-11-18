package com.ape.badminton.service;

import com.ape.badminton.entity.User;

/**
 * 比赛发起者账号服务
 *
 * 只保留登录 / 注册所需的最小能力：
 * 系统按账号隔离数据，不存在「查看或管理他人账号」的接口。
 */
public interface UserService {

    User getUserByUsername(String username);

    boolean addUser(User user);
}
