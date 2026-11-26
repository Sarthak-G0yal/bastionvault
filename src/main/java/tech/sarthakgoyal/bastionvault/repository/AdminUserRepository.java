package tech.sarthakgoyal.bastionvault.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.sarthakgoyal.bastionvault.domain.AdminUser;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
}
