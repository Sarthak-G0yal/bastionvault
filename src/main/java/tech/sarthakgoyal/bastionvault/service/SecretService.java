package tech.sarthakgoyal.bastionvault.service;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import tech.sarthakgoyal.bastionvault.crypto.EncryptionEngine;
import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.model.SecretDocument;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;

@Service
public class SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionEngine encryptionEngine;
    private final VaultSessionManager vaultSessionManager;

    public SecretService(SecretRepository secretRepository, EncryptionEngine encryptionEngine, VaultSessionManager vaultSessionManager) {
        this.secretRepository = secretRepository;
        this.encryptionEngine = encryptionEngine;
        this.vaultSessionManager = vaultSessionManager;
    }

    public void encryptAndSave(String key, String rawValue) throws Exception {
        SecretKey activeKey = vaultSessionManager.getActiveKey();

        // Capture the strict Record payload
        EncryptionEngine.CryptoPayload payload = encryptionEngine.encrypt(rawValue, activeKey);

        SecretDocument document = new SecretDocument();
        document.setKey(key);
        // Explicitly grab the named variables
        document.setCiphertext(payload.ciphertext());
        document.setIv(payload.iv());
        document.setLastUpdated(LocalDateTime.now());

        secretRepository.save(document);
    }

    public Optional<String> fetchAndDecrypt(String key) throws Exception {
        Optional<SecretDocument> documentOpt = secretRepository.findById(key);

        if (documentOpt.isEmpty()) {
            return Optional.empty();
        }

        SecretDocument doc = documentOpt.get();
        SecretKey activeKey = vaultSessionManager.getActiveKey();

        String decryptedValue = encryptionEngine.decrypt(doc.getCiphertext(), doc.getIv(), activeKey);
        return Optional.of(decryptedValue);
    }
}
