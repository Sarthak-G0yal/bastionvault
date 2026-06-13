package tech.sarthakgoyal.bastionvault;

import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.model.SecretDocument;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;
import tech.sarthakgoyal.bastionvault.service.SecretService;

@SpringBootApplication
public class BastionvaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(BastionvaultApplication.class, args);
    }

    @Bean
    public CommandLineRunner testCryptographicCore(
            VaultSessionManager sessionManager,
            SecretService secretService,
            SecretRepository secretRepository) {

        return args -> {
            System.out.println("\n===============================================");
            System.out.println("🧹 1. Wiping MongoDB collection for a clean slate...");
            secretRepository.deleteAll();
            System.out.println("✅ Database wiped.");

            try {
                System.out.println("\n🔐 2. Unlocking Vault...");
                String testMasterPassword = "my-super-strong-admin-password";
                sessionManager.unlockVault(testMasterPassword);
                System.out.println("✅ AES-256 Key derived in memory.");

                System.out.println("\n📝 3. Encrypting and Saving...");
                String secretName = "DB_PASS_TEST";
                String secretValue = "homelab-db-12345";
                secretService.encryptAndSave(secretName, secretValue);
                System.out.println("✅ Saved to MongoDB.");

                System.out.println("\n🔍 4. X-Ray Database Check...");
                Optional<SecretDocument> rawDoc = secretRepository.findById(secretName);
                if (rawDoc.isPresent()) {
                    System.out.println("   -> Ciphertext length: " + rawDoc.get().getCiphertext().length() + " chars (Should be ~44)");
                    System.out.println("   -> IV length: " + rawDoc.get().getIv().length() + " chars (Should be exactly 16)");
                }

                System.out.println("\n🔓 5. Fetching and Decrypting...");
                Optional<String> decryptedResult = secretService.fetchAndDecrypt(secretName);

                if (decryptedResult.isPresent() && decryptedResult.get().equals(secretValue)) {
                    System.out.println("✅ SUCCESS! Values match.");
                    System.out.println("   Original: " + secretValue);
                    System.out.println("   Decrypted: " + decryptedResult.get());
                } else {
                    System.err.println("❌ Decryption failed!");
                }

                // sessionManager.lockVault();
                // System.out.println("\n🔒 Vault Locked.");

            } catch (Exception e) {
                System.err.println("\n❌ TEST FAILED: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("===============================================\n");

            // Force the app to stop after the test so you don't have to hit Ctrl+C
            // System.exit(0);
        };
    }
}
