package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.service.UserService;
import com.chat.older.xiechen.util.MD5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public Result register(User user) {
        if (userMapper.countByUsername(user.getUsername()) > 0) {
            return Result.error("账号已被占用，请换一个试试");
        }
        user.setPassword(MD5Util.encrypt(user.getPassword()));
        int rows = userMapper.insertUser(user);
        if (rows > 0) {
            return Result.success(null);
        } else {
            return Result.error("服务器开小差了，注册失败");
        }
    }

    @Override
    public Result login(User user, HttpSession session) {
        String encryptedPassword = MD5Util.encrypt(user.getPassword());
        User dbUser = userMapper.login(user.getUsername(), encryptedPassword);

        if (dbUser != null) {
            session.setAttribute("currUser", dbUser);
            return Result.success(dbUser);
        } else {
            return Result.error("账号或密码错误");
        }
    }
}