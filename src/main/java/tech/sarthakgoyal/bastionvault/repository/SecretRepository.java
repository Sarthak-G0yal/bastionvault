package tech.sarthakgoyal.bastionvault.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.sarthakgoyal.bastionvault.domain.Secret;

public interface SecretRepository extends JpaRepository<Secret, Long> {
    Optional<Secret> findByGroupNameAndSecretKey(String groupName, String secretKey);

    List<Secret> findByGroupNameOrderBySecretKeyAsc(String groupName);

    List<Secret> findAllByOrderByGroupNameAscSecretKeyAsc();

    List<Secret> findByEncVersionLessThan(int encVersion);
}
