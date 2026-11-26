package tech.sarthakgoyal.bastionvault.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from AuditEvent e where e.occurredAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
