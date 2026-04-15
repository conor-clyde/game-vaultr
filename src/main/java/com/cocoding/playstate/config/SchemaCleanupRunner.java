package com.cocoding.playstate.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SchemaCleanupRunner {

  private static final Logger logger = LoggerFactory.getLogger(SchemaCleanupRunner.class);

  @Bean
  ApplicationRunner removeLegacyPlayLogColumn(JdbcTemplate jdbcTemplate) {
    return args -> {
      try {
        jdbcTemplate.execute(
            "ALTER TABLE game_play_logs DROP COLUMN IF EXISTS counts_toward_library_playtime");
      } catch (Exception ex) {
        // Startup should not fail if the current DB user cannot alter schema.
        logger.debug("Skipping legacy play-log column cleanup: {}", ex.getMessage());
      }
    };
  }

  @Bean
  ApplicationRunner addPlaythroughCreatedAtIfMissing(JdbcTemplate jdbcTemplate) {
    return args -> {
      try {
        jdbcTemplate.execute(
            "ALTER TABLE user_game_playthroughs ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute(
            "UPDATE user_game_playthroughs SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL");
      } catch (Exception ex) {
        // Startup should not fail if the current DB user cannot alter schema.
        logger.debug("Skipping playthrough created_at schema update: {}", ex.getMessage());
      }
    };
  }
}
