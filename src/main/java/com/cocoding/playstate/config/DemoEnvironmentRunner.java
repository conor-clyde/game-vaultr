package com.cocoding.playstate.config;

import com.cocoding.playstate.service.DemoDataSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class DemoEnvironmentRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoEnvironmentRunner.class);

    private final DemoDataSeedService demoDataSeedService;

    public DemoEnvironmentRunner(DemoDataSeedService demoDataSeedService) {
        this.demoDataSeedService = demoDataSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            demoDataSeedService.seedIfEnabled();
        } catch (Exception e) {
            // Keep app startup healthy even if external IGDB demo seeding fails.
            logger.warn("Demo seed failed at startup; continuing without seeding.", e);
        }
    }
}
