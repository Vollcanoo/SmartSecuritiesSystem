package com.trading.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.admin.entity.User;
import com.trading.admin.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * trading-admin 用户 API 测试
 * 覆盖 UserController 全部接口
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private static final String BASE_URL = "/api/users";

    // ---------- 创建用户 ----------
    @Test
    void createUser_shouldReturn200AndUser() throws Exception {
        UserController.CreateUserRequest request = new UserController.CreateUserRequest();
        request.setUsername("test_user");

        User saved = new User();
        saved.setId(1L);
        saved.setUid(10001L);
        saved.setShareholderId("SH00010001");
        saved.setUsername("test_user");
        saved.setStatus(1);
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());

        when(userService.createUser(eq("test_user"))).thenReturn(saved);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.uid").value(10001))
                .andExpect(jsonPath("$.data.shareholderId").value("SH00010001"))
                .andExpect(jsonPath("$.data.username").value("test_user"))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(userService, times(1)).createUser(eq("test_user"));
    }

    @Test
    void createUser_whenUsernameExists_shouldReturn409() throws Exception {
        UserController.CreateUserRequest request = new UserController.CreateUserRequest();
        request.setUsername("test_user");

        when(userService.createUser(eq("test_user")))
                .thenThrow(new IllegalStateException("用户名已存在: test_user"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在: test_user"));

        verify(userService, times(1)).createUser(eq("test_user"));
    }

    // ---------- 根据 uid 查询用户 ----------
    @Test
    void getUser_byUid_whenFound_shouldReturn200() throws Exception {
        Long uid = 10001L;
        User user = new User();
        user.setId(1L);
        user.setUid(uid);
        user.setShareholderId("A123456789");
        user.setUsername("test_user");
        user.setStatus(1);

        when(userService.getUserByUid(uid)).thenReturn(Optional.of(user));

        mockMvc.perform(get(BASE_URL + "/" + uid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uid").value(10001))
                .andExpect(jsonPath("$.data.username").value("test_user"));
    }

    @Test
    void getUser_byUid_whenNotFound_shouldReturn404() throws Exception {
        when(userService.getUserByUid(99999L)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE_URL + "/99999"))
                .andExpect(status().isNotFound());
    }

    // ---------- 根据股东号查询用户 ----------
    @Test
    void getUserByShareholderId_whenFound_shouldReturn200() throws Exception {
        String shareholderId = "A123456789";
        User user = new User();
        user.setId(1L);
        user.setUid(10001L);
        user.setShareholderId(shareholderId);
        user.setUsername("test_user");
        user.setStatus(1);

        when(userService.getUserByShareholderId(shareholderId)).thenReturn(Optional.of(user));

        mockMvc.perform(get(BASE_URL + "/shareholder/" + shareholderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shareholderId").value("A123456789"));
    }

    @Test
    void getUserByShareholderId_whenNotFound_shouldReturn404() throws Exception {
        when(userService.getUserByShareholderId("NOTFOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE_URL + "/shareholder/NOTFOUND"))
                .andExpect(status().isNotFound());
    }

    // ---------- 查询所有用户 ----------
    @Test
    void getAllUsers_shouldReturn200AndList() throws Exception {
        User u1 = new User();
        u1.setId(1L);
        u1.setUid(10001L);
        u1.setUsername("user1");
        u1.setStatus(1);
        User u2 = new User();
        u2.setId(2L);
        u2.setUid(10002L);
        u2.setUsername("user2");
        u2.setStatus(1);

        when(userService.getAllUsers()).thenReturn(Arrays.asList(u1, u2));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].uid").value(10001))
                .andExpect(jsonPath("$.data[1].uid").value(10002));
    }

    @Test
    void getAllUsers_whenEmpty_shouldReturn200AndEmptyArray() throws Exception {
        when(userService.getAllUsers()).thenReturn(Arrays.asList());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ---------- 禁用用户 ----------
    @Test
    void suspendUser_shouldReturn200() throws Exception {
        Long uid = 10001L;
        doNothing().when(userService).suspendUser(uid);

        mockMvc.perform(post(BASE_URL + "/" + uid + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("User suspended"));

        verify(userService, times(1)).suspendUser(uid);
    }

    // ---------- 启用用户 ----------
    @Test
    void resumeUser_shouldReturn200() throws Exception {
        Long uid = 10001L;
        doNothing().when(userService).resumeUser(uid);

        mockMvc.perform(post(BASE_URL + "/" + uid + "/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("User resumed"));

        verify(userService, times(1)).resumeUser(uid);
    }
}
