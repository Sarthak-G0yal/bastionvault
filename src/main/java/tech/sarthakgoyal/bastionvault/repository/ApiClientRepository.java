package tech.sarthakgoyal.bastionvault.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.sarthakgoyal.bastionvault.domain.ApiClient;

public interface ApiClientRepository extends JpaRepository<ApiClient, String> {
}
