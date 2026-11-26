package tech.sarthakgoyal.bastionvault;

import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.service.SetupService;

/** Shared vault state: all test contexts use the same in-memory H2 database. */
public final class TestFixtures {

    public static final String MASTER = "master-password-123";
    public static String recoveryKey;

    private TestFixtures() {
    }

    public static synchronized void initializedAndUnsealed(SetupService setup, KeyManager keys) {
        if (!setup.isInitialized()) {
            recoveryKey = setup.initialize("test-setup-token", "admin", "login-password-123", MASTER);
        }
        if (keys.isSealed() && !keys.unsealWithPassword(MASTER.toCharArray())) {
            throw new IllegalStateException("Test vault could not be unsealed");
        }
    }
}
