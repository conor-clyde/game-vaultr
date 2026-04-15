package com.cocoding.playstate.config;

import com.cocoding.playstate.service.DemoDataSeedService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class DemoEnvironmentRunner implements ApplicationRunner {

    private final DemoDataSeedService demoDataSeedService;

    public DemoEnvironmentRunner(DemoDataSeedService demoDataSeedService) {
        this.demoDataSeedService = demoDataSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        demoDataSeedService.seedIfEnabled();
    }
}
