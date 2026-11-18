package com.ape.badminton.service.impl;

import com.ape.badminton.entity.User;
import com.ape.badminton.mapper.UserMapper;
import com.ape.badminton.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public User getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username.trim());
        return userMapper.selectOne(wrapper);
    }

    @Override
    public boolean addUser(User user) {
        return userMapper.insert(user) > 0;
    }
}
