package tech.sarthakgoyal.bastionvault.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import tech.sarthakgoyal.bastionvault.model.AdminProfile;

@Repository
public interface AdminProfileRepository extends MongoRepository<AdminProfile, String> {
}
