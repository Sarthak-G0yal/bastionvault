package tech.sarthakgoyal.bastionvault.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.sarthakgoyal.bastionvault.domain.PolicyRule;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {
    List<PolicyRule> findByClientIdOrderByGroupPatternAsc(String clientId);
}
