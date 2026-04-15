package com.cocoding.playstate.service;

import com.cocoding.playstate.model.UserAccount;
import com.cocoding.playstate.repository.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserRegistrationService {

  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,64}$");

  private static final int MIN_PASSWORD_LENGTH = 8;

  private static final int MAX_EMAIL_LENGTH = 254;

  private final UserAccountRepository userAccountRepository;

  private final PasswordEncoder passwordEncoder;

  public UserRegistrationService(
      UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public Optional<String> validateAndRegister(
      String rawUsername, String rawEmail, String password, String confirmPassword) {
    if (rawUsername == null || rawUsername.isBlank()) {
      return Optional.of("Choose a username.");
    }
    String username = rawUsername.trim().toLowerCase(Locale.ROOT);
    if (!USERNAME_PATTERN.matcher(username).matches()) {
      return Optional.of(
          "Username must be 3–64 characters and use letters, numbers, or underscores only.");
    }
    Optional<String> emailError = validateEmail(rawEmail);
    if (emailError.isPresent()) {
      return emailError;
    }
    String email = rawEmail.trim().toLowerCase(Locale.ROOT);
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      return Optional.of("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
    }
    if (!password.equals(confirmPassword)) {
      return Optional.of("Passwords do not match.");
    }
    if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
      return Optional.of("That username is already taken.");
    }
    if (userAccountRepository.existsByEmailIgnoreCase(email)) {
      return Optional.of("That email is already registered.");
    }
    UserAccount account = new UserAccount();
    account.setUsername(username);
    account.setEmail(email);
    account.setPasswordHash(passwordEncoder.encode(password));
    userAccountRepository.save(account);
    return Optional.empty();
  }

  private static Optional<String> validateEmail(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.of("Enter your email address.");
    }
    String e = raw.trim();
    if (e.length() > MAX_EMAIL_LENGTH) {
      return Optional.of("Email is too long.");
    }
    if (e.chars().anyMatch(Character::isWhitespace)) {
      return Optional.of("Enter a valid email address.");
    }
    int at = e.lastIndexOf('@');
    if (at <= 0 || at == e.length() - 1) {
      return Optional.of("Enter a valid email address.");
    }
    String local = e.substring(0, at);
    String domain = e.substring(at + 1);
    if (local.isBlank() || domain.isBlank() || !domain.contains(".")) {
      return Optional.of("Enter a valid email address.");
    }
    return Optional.empty();
  }
}
