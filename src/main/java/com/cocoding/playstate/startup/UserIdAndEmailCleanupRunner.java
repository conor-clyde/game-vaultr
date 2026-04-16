package com.cocoding.playstate.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class UserIdAndEmailCleanupRunner implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(UserIdAndEmailCleanupRunner.class);

  private final JdbcTemplate jdbcTemplate;

  public UserIdAndEmailCleanupRunner(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    int userGamesUpdated = migrateUserIds("user_games");
    int playLogsUpdated = migrateUserIds("play_logs");
    int playthroughsUpdated = migrateUserIds("user_game_playthroughs");
    int emailsCleared = clearEmails();

    if (userGamesUpdated + playLogsUpdated + playthroughsUpdated + emailsCleared > 0) {
      logger.info(
          "User cleanup applied: user_games={}, play_logs={}, user_game_playthroughs={}, emails_cleared={}",
          userGamesUpdated,
          playLogsUpdated,
          playthroughsUpdated,
          emailsCleared);
    }
  }

  private int migrateUserIds(String tableName) {
    if (!tableExists(tableName) || !tableExists("user_accounts") || !columnExists("user_accounts", "email")) {
      return 0;
    }
    String sql =
        "update "
            + tableName
            + " t set user_id = ("
            + "select ua.username from user_accounts ua "
            + "where ua.email is not null and lower(ua.email) = lower(t.user_id)"
            + ") where exists ("
            + "select 1 from user_accounts ua "
            + "where ua.email is not null and lower(ua.email) = lower(t.user_id)"
            + ")";
    try {
      return jdbcTemplate.update(sql);
    } catch (DataAccessException e) {
      logger.warn("Skipping user ID migration for table '{}': {}", tableName, e.getMostSpecificCause().getMessage());
      return 0;
    }
  }

  private int clearEmails() {
    if (!tableExists("user_accounts") || !columnExists("user_accounts", "email")) {
      return 0;
    }
    try {
      return jdbcTemplate.update("update user_accounts set email = null where email is not null");
    } catch (DataAccessException e) {
      logger.warn("Skipping email cleanup: {}", e.getMostSpecificCause().getMessage());
      return 0;
    }
  }

  private boolean tableExists(String tableName) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
            Integer.class,
            tableName);
    return count != null && count > 0;
  }

  private boolean columnExists(String tableName, String columnName) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?",
            Integer.class,
            tableName,
            columnName);
    return count != null && count > 0;
  }
}
