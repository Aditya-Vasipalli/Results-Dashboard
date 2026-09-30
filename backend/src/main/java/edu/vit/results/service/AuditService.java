package edu.vit.results.service;

import edu.vit.results.config.AuthUser;
import edu.vit.results.domain.AuditEntry;
import edu.vit.results.repo.AuditRepository;
import edu.vit.results.service.Views.AuditView;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    /** Joins the caller's transaction, so the entry is only kept if the change itself commits. */
    @Transactional
    public void log(AuthUser actor, String action, String detail) {
        repo.save(new AuditEntry(actor.name(), action, detail));
    }

    @Transactional(readOnly = true)
    public List<AuditView> recent(int limit) {
        int n = Math.max(1, Math.min(limit, 500));
        return repo.findAllByOrderByIdDesc(PageRequest.of(0, n)).stream().map(AuditView::of).toList();
    }
}
