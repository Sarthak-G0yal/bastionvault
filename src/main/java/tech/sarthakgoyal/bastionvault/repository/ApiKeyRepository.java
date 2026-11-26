package tech.sarthakgoyal.bastionvault.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.sarthakgoyal.bastionvault.domain.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyId(String keyId);

    List<ApiKey> findByClientIdOrderByCreatedAtDesc(String clientId);
}
