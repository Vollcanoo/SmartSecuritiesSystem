package com.trading.core.controller;

import com.trading.core.engine.ExchangeEngineHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final ExchangeEngineHolder engineHolder;

    public UserController(ExchangeEngineHolder engineHolder) {
        this.engineHolder = engineHolder;
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> request) {
        Long uid;
        try {
            uid = Long.valueOf(request.get("uid").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid uid"));
        }
        
        try {
            engineHolder.initUserAndBalance(uid);
            log.info("Initialized engine user and balance for uid={}", uid);
            return ResponseEntity.ok(Map.of("status", "success", "uid", uid));
        } catch (Exception e) {
            log.error("Failed to init user in engine for uid={}", uid, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}