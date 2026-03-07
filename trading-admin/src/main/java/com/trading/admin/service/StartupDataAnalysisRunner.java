package com.trading.admin.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupDataAnalysisRunner implements ApplicationRunner {

    private final StartupDataAnalysisService startupDataAnalysisService;

    public StartupDataAnalysisRunner(StartupDataAnalysisService startupDataAnalysisService) {
        this.startupDataAnalysisService = startupDataAnalysisService;
    }

    @Override
    public void run(ApplicationArguments args) {
        startupDataAnalysisService.generateStartupReport();
    }
}
