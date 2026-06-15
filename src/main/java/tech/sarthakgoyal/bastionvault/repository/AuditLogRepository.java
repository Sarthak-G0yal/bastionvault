package tech.sarthakgoyal.bastionvault.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import tech.sarthakgoyal.bastionvault.model.AuditLogDocument;

public interface AuditLogRepository extends MongoRepository<AuditLogDocument, String> {
}
