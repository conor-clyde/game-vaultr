package com.cocoding.playstate.config;

import com.cocoding.playstate.model.UserAccount;
import com.cocoding.playstate.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(1)
public class DevUserAccountSeed implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;

    private final PasswordEncoder passwordEncoder;

    @Value("${security.user.name:}")
    private String seedUsername;

    @Value("${security.user.password:}")
    private String seedPassword;

    @Value("${security.user.email:}")
    private String seedEmail;

    public DevUserAccountSeed(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedUsername == null || seedUsername.isBlank() || seedPassword == null || seedPassword.isBlank()) {
            return;
        }
        String username = seedUsername.trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(seedPassword));
        if (seedEmail != null && !seedEmail.isBlank()) {
            account.setEmail(seedEmail.trim().toLowerCase(Locale.ROOT));
        }
        userAccountRepository.save(account);
    }
}
