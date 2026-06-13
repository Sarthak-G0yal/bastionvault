package tech.sarthakgoyal.bastionvault.service;

import tech.sarthakgoyal.bastionvault.crypto.EncryptionEngine;
import tech.sarthakgoyal.bastionvault.model.SecretDocument;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionEngine encryptionEngine;

    public SecretService(SecretRepository secretRepository, EncryptionEngine encryptionEngine) {
        this.secretRepository = secretRepository;
        this.encryptionEngine = encryptionEngine;
    }

    /**
     * Encrypts the raw secret and saves it to MongoDB.
     */
    public void encryptAndSave(String key, String rawValue) throws Exception {
        SecretKey activeKey = getActiveKey(); // We will replace this in Phase 2

        String[] cryptoResult = encryptionEngine.encrypt(rawValue, activeKey);
        String ciphertext = cryptoResult[0];
        String iv = cryptoResult[1];

        SecretDocument document = new SecretDocument(key, ciphertext, iv, LocalDateTime.now());
        secretRepository.save(document);
    }

    /**
     * Retrieves the document from MongoDB and decrypts the value.
     */
    public Optional<String> fetchAndDecrypt(String key) throws Exception {
        Optional<SecretDocument> documentOpt = secretRepository.findById(key);

        if (documentOpt.isEmpty()) {
            return Optional.empty();
        }

        SecretDocument doc = documentOpt.get();
        SecretKey activeKey = getActiveKey(); // We will replace this in Phase 2

        String decryptedValue = encryptionEngine.decrypt(doc.getCiphertext(), doc.getIv(), activeKey);
        return Optional.of(decryptedValue);
    }

    /**
     * TEMPORARY PHASE 1 METHOD: Generates a weak, static key just so the app
     * compiles and runs. In Phase 2, this will be deleted and replaced with our
     * secure PBKDF2 logic.
     */
    private SecretKey getActiveKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest("temporary-phase1-dev-key".getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
