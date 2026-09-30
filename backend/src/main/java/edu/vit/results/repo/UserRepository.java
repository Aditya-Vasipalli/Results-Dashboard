package edu.vit.results.repo;

import edu.vit.results.domain.AppUser;
import edu.vit.results.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findAllByOrderByRoleAscNameAsc();
    List<AppUser> findAllByRoleOrderByNameAsc(Role role);
    List<AppUser> findAllByRoleOrderByPrnAsc(Role role);
    long countByRole(Role role);
}
