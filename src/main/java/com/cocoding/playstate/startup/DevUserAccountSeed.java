package com.cocoding.playstate.startup;

import com.cocoding.playstate.model.UserAccount;
import com.cocoding.playstate.repository.UserAccountRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DevUserAccountSeed implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(DevUserAccountSeed.class);

  private final UserAccountRepository userAccountRepository;

  private final PasswordEncoder passwordEncoder;

  @Value("${security.user.name:}")
  private String seedUsername;

  @Value("${security.user.password:}")
  private String seedPassword;

  @Value("${security.user.email:}")
  private String seedEmail;

  public DevUserAccountSeed(
      UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (seedUsername == null
        || seedUsername.isBlank()
        || seedPassword == null
        || seedPassword.isBlank()) {
      return;
    }
    String username = seedUsername.trim().toLowerCase(Locale.ROOT);
    if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
      return;
    }
    String normalizedEmail = null;
    if (seedEmail != null && !seedEmail.isBlank()) {
      normalizedEmail = seedEmail.trim().toLowerCase(Locale.ROOT);
      if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
        return;
      }
    }
    UserAccount account = new UserAccount();
    account.setUsername(username);
    account.setPasswordHash(passwordEncoder.encode(seedPassword));
    if (normalizedEmail != null) {
      account.setEmail(normalizedEmail);
    }
    try {
      userAccountRepository.save(account);
    } catch (DataIntegrityViolationException e) {
      // Startup can race with another account create flow; skip if uniqueness is already satisfied.
      logger.warn(
          "Dev seed user '{}' not created because username/email already exists.", username);
    }
  }
}
