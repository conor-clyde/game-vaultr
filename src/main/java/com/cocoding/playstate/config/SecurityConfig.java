package com.cocoding.playstate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Value("${spring.h2.console.enabled:false}")
  private boolean h2ConsoleEnabled;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      "/", "/account", "/login", "/register", "/error", "/css/**", "/js/**")
                  .permitAll();
              if (h2ConsoleEnabled) {
                auth.requestMatchers("/h2-console/**").permitAll();
              }
              auth.requestMatchers("/collection", "/collection/**", "/search", "/search/**")
                  .authenticated()
                  .anyRequest()
                  .authenticated();
            })
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) ->
                        response.sendRedirect(request.getContextPath() + "/")))
        .formLogin(form -> form.loginPage("/login").permitAll().defaultSuccessUrl("/", true))
        .logout(logout -> logout.logoutSuccessUrl("/").permitAll());

    if (h2ConsoleEnabled) {
      // Allow H2 console to render in local development only.
      http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
          .headers(headers -> headers.frameOptions(frame -> frame.disable()));
    }

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
