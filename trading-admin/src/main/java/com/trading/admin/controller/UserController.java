// trading-admin/src/main/java/com/trading/admin/controller/UserController.java
package com.trading.admin.controller;

import com.trading.admin.entity.User;
import com.trading.admin.dto.UserAnalysisDTO;
import com.trading.admin.service.UserAnalysisService;
import com.trading.admin.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserAnalysisService userAnalysisService;

    /**
     * 创建用户：系统自动生成 uid 和股东号，用户只需提供用户名。
     * 若用户名已存在返回 409 Conflict。
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createUser(@RequestBody CreateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "用户名不能为空"));
        }

        try {
            User created = userService.createUser(request.getUsername().trim());
            return ResponseEntity.ok(ApiResponse.success(created));
        } catch (IllegalStateException e) {
            log.warn("Create user conflict: {}", e.getMessage());
            return ResponseEntity.status(409).body(ApiResponse.error(409, e.getMessage()));
        }
    }

    /**
     * 查询用户
     */
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse> getUser(@PathVariable Long uid) {
        Optional<User> user = userService.getUserByUid(uid);
        return user.map(value -> ResponseEntity.ok(ApiResponse.success(value))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 根据股东号查询用户
     */
    @GetMapping("/shareholder/{shareholderId}")
    public ResponseEntity<ApiResponse> getUserByShareholderId(@PathVariable String shareholderId) {
        Optional<User> user = userService.getUserByShareholderId(shareholderId);
        return user.map(value -> ResponseEntity.ok(ApiResponse.success(value))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 查询所有用户
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * 禁用用户
     */
    @PostMapping("/{uid}/suspend")
    public ResponseEntity<ApiResponse> suspendUser(@PathVariable Long uid) {
        userService.suspendUser(uid);
        return ResponseEntity.ok(ApiResponse.success(null, "User suspended"));
    }

    /**
     * 启用用户
     */
    @PostMapping("/{uid}/resume")
    public ResponseEntity<ApiResponse> resumeUser(@PathVariable Long uid) {
        userService.resumeUser(uid);
        return ResponseEntity.ok(ApiResponse.success(null, "User resumed"));
    }

    /**
     * 修改用户名
     */
    @PutMapping("/{uid}/username")
    public ResponseEntity<ApiResponse> updateUsername(@PathVariable Long uid, @RequestBody UpdateUsernameRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "用户名不能为空"));
        }
        
        try {
            User updated = userService.updateUsername(uid, request.getUsername().trim());
            return ResponseEntity.ok(ApiResponse.success(updated, "用户名修改成功"));
        } catch (IllegalStateException e) {
            log.warn("Update username failed: {}", e.getMessage());
            return ResponseEntity.status(409).body(ApiResponse.error(409, e.getMessage()));
        }
    }

    /**
     * 查询用户余额（简化版：当前引擎固定 uid=1，所有用户共享余额）
     * 实际生产环境需要根据 shareholderId 映射到不同的引擎 uid
     */
    @GetMapping("/{uid}/balance")
    public ResponseEntity<ApiResponse> getUserBalance(@PathVariable Long uid) {
        // 简化实现：返回固定余额信息
        // 实际应该从引擎查询，但当前引擎架构是固定 uid=1
        BalanceInfo balance = new BalanceInfo();
        balance.setUid(uid);
        balance.setQuoteBalance(1000000.0); // 报价货币余额（资金）
        balance.setBaseBalance(10000.0);    // 基础货币余额（持仓）
        balance.setNote("当前为简化实现，所有用户共享引擎余额");
        return ResponseEntity.ok(ApiResponse.success(balance));
    }

    /**
     * 用户交易分析：在指定时间区间内统计单个用户的订单与成交情况。
     *
     * URL: GET /api/users/{uid}/analysis?start=2026-03-01T00:00:00&end=2026-03-07T23:59:59
     */
    @GetMapping("/{uid}/analysis")
    public ResponseEntity<ApiResponse> analyzeUser(
            @PathVariable Long uid,
            @RequestParam("start") String start,
            @RequestParam("end") String end
    ) {
        if (start == null || start.isEmpty() || end == null || end.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "start 和 end 时间不能为空"));
        }

        try {
            LocalDateTime startTime = LocalDateTime.parse(start);
            LocalDateTime endTime = LocalDateTime.parse(end);

            if (endTime.isBefore(startTime)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "end 时间不能早于 start 时间"));
            }

            UserAnalysisDTO analysis = userAnalysisService.analyze(uid, startTime, endTime);
            return ResponseEntity.ok(ApiResponse.success(analysis));
        } catch (DateTimeParseException e) {
            log.warn("Invalid datetime format for user analysis, start={}, end={}", start, end);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "时间格式不正确，期望 yyyy-MM-dd'T'HH:mm[:ss]"));
        } catch (IllegalArgumentException e) {
            log.warn("User analysis failed: {}", e.getMessage());
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            log.error("User analysis error", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error(500, "用户分析失败: " + e.getMessage()));
        }
    }

    // DTO类
    public static class CreateUserRequest {
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    public static class UpdateUsernameRequest {
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    public static class BalanceInfo {
        private Long uid;
        private Double quoteBalance; // 报价货币余额（资金）
        private Double baseBalance;  // 基础货币余额（持仓）
        private String note;

        public Long getUid() {
            return uid;
        }

        public void setUid(Long uid) {
            this.uid = uid;
        }

        public Double getQuoteBalance() {
            return quoteBalance;
        }

        public void setQuoteBalance(Double quoteBalance) {
            this.quoteBalance = quoteBalance;
        }

        public Double getBaseBalance() {
            return baseBalance;
        }

        public void setBaseBalance(Double baseBalance) {
            this.baseBalance = baseBalance;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public static class ApiResponse {
        private int code;
        private String message;
        private Object data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public static ApiResponse success(Object data) {
            ApiResponse response = new ApiResponse();
            response.code = 0;
            response.message = "success";
            response.data = data;
            return response;
        }

        public static ApiResponse success(Object data, String message) {
            ApiResponse response = new ApiResponse();
            response.code = 0;
            response.message = message;
            response.data = data;
            return response;
        }

        public static ApiResponse error(int code, String message) {
            ApiResponse response = new ApiResponse();
            response.code = code;
            response.message = message;
            response.data = null;
            return response;
        }
    }
}
