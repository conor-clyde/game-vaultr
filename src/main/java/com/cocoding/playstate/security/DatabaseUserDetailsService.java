package com.cocoding.playstate.security;

import com.cocoding.playstate.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final UserAccountRepository userAccountRepository;

  public DatabaseUserDetailsService(UserAccountRepository userAccountRepository) {
    this.userAccountRepository = userAccountRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String login = username == null ? "" : username.trim();
    return userAccountRepository
        .findByEmailIgnoreCase(login)
        .or(() -> userAccountRepository.findByUsernameIgnoreCase(login))
        .map(
            account ->
                User.withUsername(account.getEmail() != null ? account.getEmail() : account.getUsername())
                    .password(account.getPasswordHash())
                    .roles("USER")
                    .build())
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }
}
