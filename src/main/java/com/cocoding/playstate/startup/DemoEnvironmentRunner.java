package com.cocoding.playstate.startup;

import com.cocoding.playstate.igdb.IgdbTokenService;
import com.cocoding.playstate.service.DemoDataSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class DemoEnvironmentRunner implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(DemoEnvironmentRunner.class);

  private final DemoDataSeedService demoDataSeedService;
  private final IgdbTokenService igdbTokenService;
  private final Environment environment;

  @Value("${igdb.client.id:}")
  private String igdbClientId;

  @Value("${igdb.client.secret:}")
  private String igdbClientSecret;

  public DemoEnvironmentRunner(
      DemoDataSeedService demoDataSeedService,
      IgdbTokenService igdbTokenService,
      Environment environment) {
    this.demoDataSeedService = demoDataSeedService;
    this.igdbTokenService = igdbTokenService;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (isLocalProfileActive()) {
      validateLocalIgdbCredentials();
    }
    try {
      demoDataSeedService.seedIfEnabled();
    } catch (Exception e) {
      // Keep app startup healthy even if external IGDB demo seeding fails.
      logger.warn("Demo seed failed at startup; continuing without seeding.", e);
    }
  }

  private boolean isLocalProfileActive() {
    for (String profile : environment.getActiveProfiles()) {
      if ("local".equalsIgnoreCase(profile)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void validateLocalIgdbCredentials() {
    if (isBlank(igdbClientId) || isBlank(igdbClientSecret)) {
      logger.warn(
          "Local profile started without IGDB credentials. Set igdb.client.id and"
              + " igdb.client.secret in application-local.properties.");
      return;
    }
    try {
      igdbTokenService.getAccessToken();
      logger.info("Local IGDB credential check passed.");
    } catch (Exception e) {
      logger.warn("Local IGDB credential check failed: {}", rootMessage(e));
    }
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() != null ? current.getMessage() : current.toString();
  }
}
