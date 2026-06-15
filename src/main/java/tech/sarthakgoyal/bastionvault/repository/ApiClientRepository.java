package tech.sarthakgoyal.bastionvault.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import tech.sarthakgoyal.bastionvault.model.ApiClientDocument;

public interface ApiClientRepository extends MongoRepository<ApiClientDocument, String> {
}
