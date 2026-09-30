package edu.vit.results.repo;

import edu.vit.results.domain.AuditEntry;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findAllByOrderByIdDesc(Pageable page);
}
