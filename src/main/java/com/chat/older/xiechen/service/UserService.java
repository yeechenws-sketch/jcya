package com.chat.older.xiechen.service;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.model.User;

import jakarta.servlet.http.HttpSession;

public interface UserService {
    Result register(User user);
    Result login(User user, HttpSession session);
}
