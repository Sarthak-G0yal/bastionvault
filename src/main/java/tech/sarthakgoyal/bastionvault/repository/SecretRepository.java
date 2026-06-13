package tech.sarthakgoyal.bastionvault.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import tech.sarthakgoyal.bastionvault.model.SecretDocument;

public interface SecretRepository extends MongoRepository<SecretDocument, String> {

}
