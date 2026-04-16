package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByUsernameIgnoreCase(String username);

  Optional<UserAccount> findByEmailIgnoreCase(String email);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);
}
