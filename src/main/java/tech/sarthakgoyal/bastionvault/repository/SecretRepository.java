package tech.sarthakgoyal.bastionvault.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import tech.sarthakgoyal.bastionvault.model.SecretDocument;

public interface SecretRepository extends MongoRepository<SecretDocument, String> {
    List<SecretDocument> findByApplicationGroup(String applicationGroup);

    Optional<SecretDocument> findByKey(String key);
}
