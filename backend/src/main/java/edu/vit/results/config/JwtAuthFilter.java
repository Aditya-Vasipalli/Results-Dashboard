package edu.vit.results.config;

import edu.vit.results.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the Bearer token and loads the user from the database, so a deleted account or a
 * changed role takes effect on the very next request. Not a @Component on purpose: it is
 * added to the security chain only, not registered a second time as a servlet filter.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.verify(header.substring(7).trim()).flatMap(users::findById).ifPresent(u -> {
                var principal = AuthUser.of(u);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }
}
