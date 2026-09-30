package edu.vit.results.config;

import edu.vit.results.repo.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, UserRepository users) throws Exception {
        http
            .csrf(c -> c.disable()) // stateless Bearer tokens, no cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .requestMatchers("/api/subjects/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()) // the React app and its assets
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "Please sign in again"))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "You do not have access to this")))
            .addFilterBefore(new JwtAuthFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Stops Spring Boot from creating its default in-memory user; we authenticate with JWTs only. */
    @Bean
    UserDetailsService noUserDetails() {
        return username -> { throw new UsernameNotFoundException(username); };
    }

    private static void json(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
