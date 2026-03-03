package io.agentrunr.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration. Stateless API with CSRF disabled.
 * API key authentication will be added in Phase 5.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/setup", "/setup.html", "/css/**", "/js/**", "/*.png").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/dashboard/**").permitAll()
                        .anyRequest().denyAll()
                );
        return http.build();
    }
}
