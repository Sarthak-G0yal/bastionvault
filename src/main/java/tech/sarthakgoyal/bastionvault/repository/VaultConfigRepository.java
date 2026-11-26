package tech.sarthakgoyal.bastionvault.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.sarthakgoyal.bastionvault.domain.VaultConfig;

public interface VaultConfigRepository extends JpaRepository<VaultConfig, Short> {
}
