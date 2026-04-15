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
    return userAccountRepository
        .findByUsernameIgnoreCase(username.trim())
        .map(
            account ->
                User.withUsername(account.getUsername())
                    .password(account.getPasswordHash())
                    .roles("USER")
                    .build())
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }
}
