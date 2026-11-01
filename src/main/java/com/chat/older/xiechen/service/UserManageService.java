package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.UserManageMapper;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.util.MD5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserManageService {

    @Autowired
    private UserManageMapper userManageMapper;

    public List<User> getAllUsers() {
        return userManageMapper.findAll();
    }

    public User getUserById(Long id) {
        return userManageMapper.findById(id);
    }

    public User getUserByUsername(String username) {
        return userManageMapper.findByUsername(username);
    }

    public void createUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(MD5Util.encrypt(user.getPassword()));
        }
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("USER");
        }
        userManageMapper.insert(user);
    }

    public void updateUser(User user) {
        userManageMapper.update(user);
    }

    public void updatePassword(Long id, String newPassword) {
        String encoded = MD5Util.encrypt(newPassword);
        userManageMapper.updatePassword(id, encoded);
    }

    public void deleteUser(Long id) {
        userManageMapper.deleteById(id);
    }

    public int getUserCount() {
        return userManageMapper.countAll();
    }

    public int getUserCountByRole(String role) {
        return userManageMapper.countByRole(role);
    }
}
