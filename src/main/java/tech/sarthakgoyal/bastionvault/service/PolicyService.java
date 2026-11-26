package tech.sarthakgoyal.bastionvault.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.domain.PolicyRule;
import tech.sarthakgoyal.bastionvault.repository.ApiClientRepository;
import tech.sarthakgoyal.bastionvault.repository.PolicyRuleRepository;

/**
 * Per-client access rules (decision 2B). Deny by default: a client can only touch a group when at
 * least one of its rules matches the group name and grants the capability.
 */
@Service
public class PolicyService {

    public enum Capability { READ, WRITE }

    private final PolicyRuleRepository rules;
    private final ApiClientRepository clients;

    public PolicyService(PolicyRuleRepository rules, ApiClientRepository clients) {
        this.rules = rules;
        this.clients = clients;
    }

    public boolean allows(String clientId, String group, Capability capability) {
        return rules.findByClientIdOrderByGroupPatternAsc(clientId).stream()
                .filter(rule -> capability == Capability.READ ? rule.isCanRead() : rule.isCanWrite())
                .anyMatch(rule -> GlobMatcher.matches(rule.getGroupPattern(), group));
    }

    /** @throws PolicyDeniedException when not allowed */
    public void require(String clientId, String group, Capability capability) {
        if (!allows(clientId, group, capability)) {
            throw new PolicyDeniedException(
                    "Client '" + clientId + "' has no " + capability.name().toLowerCase() + " access to '" + group + "'");
        }
    }

    public List<PolicyRule> rulesFor(String clientId) {
        return rules.findByClientIdOrderByGroupPatternAsc(clientId);
    }

    @Transactional
    public PolicyRule addRule(String clientId, String groupPattern, boolean canRead, boolean canWrite) {
        if (!clients.existsById(clientId)) {
            throw new NotFoundException("Unknown client " + clientId);
        }
        if (!canRead && !canWrite) {
            throw new IllegalArgumentException("A rule must grant read, write or both");
        }
        return rules.save(new PolicyRule(clientId, Names.groupPattern(groupPattern), canRead, canWrite));
    }

    @Transactional
    public void removeRule(Long ruleId) {
        rules.deleteById(ruleId);
    }
}
