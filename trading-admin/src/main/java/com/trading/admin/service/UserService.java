// trading-admin/src/main/java/com/trading/admin/service/UserService.java
package com.trading.admin.service;

import com.trading.admin.entity.User;
import com.trading.admin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * 创建用户：系统自动生成 uid 和股东号，用户只需提供用户名。
     * 若用户名已存在则抛出 IllegalStateException。
     */
    @Transactional
    public User createUser(String username) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("用户名已存在: " + username);
        }
        
        // 自动生成 uid：查找最大 uid，+1
        Long maxUid = userRepository.findAll().stream()
                .mapToLong(User::getUid)
                .max()
                .orElse(10000L); // 如果没有用户，从 10000 开始
        Long newUid = maxUid + 1;
        
        // 自动生成股东号：格式 SH + 8位数字（不足补0）
        // 如果uid超过8位，取后8位
        long uidForShareholder = newUid % 100000000L; // 确保不超过8位
        String newShareholderId = String.format("SH%08d", uidForShareholder);
        
        // 检查股东号是否已存在（理论上不会，但保险起见）
        while (userRepository.findByShareholderId(newShareholderId).isPresent()) {
            newUid++;
            uidForShareholder = newUid % 100000000L;
            newShareholderId = String.format("SH%08d", uidForShareholder);
        }
        
        User user = new User();
        user.setUid(newUid);
        user.setShareholderId(newShareholderId);
        user.setUsername(username);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        User saved = userRepository.save(user);
        log.info("User created: uid={}, shareholderId={}, username={}", saved.getUid(), saved.getShareholderId(), saved.getUsername());
        return saved;
    }

    /**
     * 根据uid查询用户
     */
    public Optional<User> getUserByUid(Long uid) {
        return userRepository.findByUid(uid);
    }

    /**
     * 根据股东号查询用户
     */
    public Optional<User> getUserByShareholderId(String shareholderId) {
        return userRepository.findByShareholderId(shareholderId);
    }

    /**
     * 查询所有用户
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void suspendUser(Long uid) {
        Optional<User> userOpt = userRepository.findByUid(uid);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setStatus(0);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("User suspended: uid={}", uid);
        }
    }

    /**
     * 启用用户
     */
    @Transactional
    public void resumeUser(Long uid) {
        Optional<User> userOpt = userRepository.findByUid(uid);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setStatus(1);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("User resumed: uid={}", uid);
        }
    }

    /**
     * 修改用户名。若新用户名已存在则抛出 IllegalStateException。
     */
    @Transactional
    public User updateUsername(Long uid, String newUsername) {
        Optional<User> userOpt = userRepository.findByUid(uid);
        if (!userOpt.isPresent()) {
            throw new IllegalStateException("用户不存在: uid=" + uid);
        }
        
        // 检查新用户名是否已被其他用户使用
        Optional<User> existingUser = userRepository.findByUsername(newUsername);
        if (existingUser.isPresent() && !existingUser.get().getUid().equals(uid)) {
            throw new IllegalStateException("用户名已存在: " + newUsername);
        }
        
        User user = userOpt.get();
        user.setUsername(newUsername);
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        log.info("Username updated: uid={}, newUsername={}", uid, newUsername);
        return saved;
    }
}