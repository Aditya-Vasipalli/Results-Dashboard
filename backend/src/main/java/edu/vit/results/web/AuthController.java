package edu.vit.results.web;

import edu.vit.results.config.AuthUser;
import edu.vit.results.config.JwtService;
import edu.vit.results.domain.AppUser;
import edu.vit.results.repo.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank(message = "Enter your email") String email,
                               @NotBlank(message = "Enter your password") String password) {}

    public record LoginResponse(String token, AuthUser user) {}

    public record PasswordChange(@NotBlank(message = "Enter your current password") String current,
                                 @NotBlank @Size(min = 6, max = 100, message = "New password must be at least 6 characters") String next) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByEmail(req.email().trim().toLowerCase())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                // Same message for unknown email and wrong password.
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Incorrect email or password"));
        return new LoginResponse(jwt.issue(u.getId()), AuthUser.of(u));
    }

    @GetMapping("/me")
    public Map<String, AuthUser> me(@AuthenticationPrincipal AuthUser me) {
        return Map.of("user", me);
    }

    @PostMapping("/change-password")
    @Transactional
    public Map<String, Boolean> changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PasswordChange req) {
        AppUser u = users.findById(me.id()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!encoder.matches(req.current(), u.getPasswordHash())) throw ApiException.badRequest("Current password is incorrect");
        u.setPasswordHash(encoder.encode(req.next()));
        users.save(u);
        return Map.of("ok", true);
    }
}
