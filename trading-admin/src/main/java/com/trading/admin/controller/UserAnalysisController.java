package com.trading.admin.controller;

import com.trading.admin.dto.UserAnalysisDTO;
import com.trading.admin.service.UserAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/analysis")
public class UserAnalysisController {

    private final UserAnalysisService service;

    public UserAnalysisController(UserAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/user/{userId}")
    public UserAnalysisDTO analyze(
            @PathVariable Long userId,
            @RequestParam String start,
            @RequestParam String end) {

        return service.analyze(
                userId,
                LocalDateTime.parse(start),
                LocalDateTime.parse(end)
        );
    }
}