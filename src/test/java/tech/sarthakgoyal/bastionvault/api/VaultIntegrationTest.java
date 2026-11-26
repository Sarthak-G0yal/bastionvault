package tech.sarthakgoyal.bastionvault.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tech.sarthakgoyal.bastionvault.TestFixtures;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.PolicyService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.service.SetupService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VaultIntegrationTest {

    static final String MASTER = TestFixtures.MASTER;

    @Autowired MockMvc mvc;
    @Autowired SetupService setup;
    @Autowired KeyManager keys;
    @Autowired ClientService clients;
    @Autowired PolicyService policies;
    @Autowired SecretService secrets;
    @Autowired SecretRepository secretRepository;
    @Autowired ObjectMapper json;

    @BeforeEach
    void initializedAndUnsealed() {
        TestFixtures.initializedAndUnsealed(setup, keys);
    }

    /** Creates a client with the given rules and returns a short-lived token for it. */
    private String clientToken(String id, String pattern, boolean read, boolean write) throws Exception {
        String secret = clients.create(id, id);
        if (pattern != null) {
            policies.addRule(id, pattern, read, write);
        }
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + id + "\",\"clientSecret\":\"" + secret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asString();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------ 1A: UI requires a real login

    @Test
    void uiRedirectsToLoginWithoutASession() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
        mvc.perform(get("/clients")).andExpect(status().is3xxRedirection());
    }

    @Test
    void setupCannotRunTwiceOrWithoutToken() {
        assertThatThrownBy(() -> setup.initialize("test-setup-token", "x", "login-password-123", "master-password-xyz"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------ API authentication

    @Test
    void healthIsPublicAndReportsSealState() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.sealed").value(false));
    }

    @Test
    void secretEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/secrets/app/KEY")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/secrets/app/KEY").header("Authorization", "Bearer bvt_nonsense"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void badClientSecretIsRejected() throws Exception {
        String id = unique("bad");
        clients.create(id, id);
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + id + "\",\"clientSecret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientIsLockedOutAfterRepeatedFailures() throws Exception {
        String id = unique("brute");
        clients.create(id, id);
        String body = "{\"clientId\":\"" + id + "\",\"clientSecret\":\"wrong\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void nonExpiringTokensCannotBeRequested() throws Exception {
        String id = unique("ttl");
        String secret = clients.create(id, id);
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + id + "\",\"clientSecret\":\"" + secret + "\",\"expiresInMinutes\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + id + "\",\"clientSecret\":\"" + secret + "\",\"expiresInMinutes\":9999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInMinutes").value(60));
    }

    // ------------------------------------------------------------ 2B: policies

    @Test
    void clientWithoutRulesIsDenied() throws Exception {
        secrets.put("policy-a", "KEY", "v", "test");
        String token = clientToken(unique("norules"), null, false, false);
        mvc.perform(get("/api/v1/secrets/policy-a/KEY").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void readRuleAllowsReadButNotWrite() throws Exception {
        secrets.put("app-one", "DB_PASS", "p@ss$word", "test");
        secrets.put("other", "DB_PASS", "nope", "test");
        String token = clientToken(unique("reader"), "app-*", true, false);
        String auth = "Bearer " + token;

        mvc.perform(get("/api/v1/secrets/app-one/DB_PASS").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("p@ss$word"));
        mvc.perform(get("/api/v1/groups/app-one").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.DB_PASS").value("p@ss$word"));
        mvc.perform(get("/api/v1/env/app-one").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(content().string("DB_PASS='p@ss$word'\n"));
        mvc.perform(get("/api/v1/secrets/other/DB_PASS").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/secrets/app-one/DB_PASS").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeRuleAllowsWriteGenerateAndDelete() throws Exception {
        String group = unique("rw");
        String auth = "Bearer " + clientToken(unique("writer"), group, true, true);

        mvc.perform(put("/api/v1/secrets/" + group + "/API_KEY").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"line1\\nline2\"}"))
                .andExpect(status().isCreated());
        assertThat(secrets.get(group, "API_KEY")).contains("line1\nline2");

        String generated = mvc.perform(post("/api/v1/secrets/" + group + "/GEN/generate").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"length\":40}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String value = json.readTree(generated).get("value").asString();
        assertThat(value).hasSize(40);
        mvc.perform(post("/api/v1/secrets/" + group + "/GEN/generate").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value(value));

        mvc.perform(delete("/api/v1/secrets/" + group + "/API_KEY").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/secrets/" + group + "/API_KEY").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ 3B: API keys

    @Test
    void apiKeyWorksUntilRevoked() throws Exception {
        String id = unique("eso");
        clients.create(id, id);
        policies.addRule(id, "eso-group", true, false);
        secrets.put("eso-group", "TOKEN", "abc", "test");
        String apiKey = clients.createApiKey(id, "eso", null);

        mvc.perform(get("/api/v1/groups/eso-group").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isOk()).andExpect(jsonPath("$.TOKEN").value("abc"));

        clients.apiKeys(id).forEach(k -> clients.revokeApiKey(k.getId()));
        mvc.perform(get("/api/v1/groups/eso-group").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAClientKillsItsTokensAndKeys() throws Exception {
        String id = unique("dis");
        String token = clientToken(id, "*", true, false);
        String apiKey = clients.createApiKey(id, "k", 30);
        clients.setActive(id, false);
        mvc.perform(get("/api/v1/health/ready").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/x").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/groups/x").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ seal state (U-C, 5B)

    @Test
    void sealedVaultReturns503AndDropsTokens() throws Exception {
        secrets.put("sealtest", "K", "v", "test");
        String id = unique("sealed");
        String token = clientToken(id, "sealtest", true, false);
        String apiKey = clients.createApiKey(id, "k", null);

        keys.seal();
        mvc.perform(get("/api/v1/health/ready")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/secrets/sealtest/K").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/secrets/sealtest/K").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + id + "\",\"clientSecret\":\"x\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void storedCiphertextIsNotPlaintext() {
        secrets.put("at-rest", "PASSWORD", "very-recognisable-value", "test");
        byte[] blob = secretRepository.findByGroupNameAndSecretKey("at-rest", "PASSWORD").orElseThrow().getValueBlob();
        assertThat(new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("very-recognisable-value");
    }

    @Test
    void unsealMethodsAndKeyRotation() {
        secrets.put("rotation", "K", "survives", "test");

        keys.seal();
        assertThat(keys.unsealWithPassword("wrong-password".toCharArray())).isFalse();
        assertThat(keys.isSealed()).isTrue();
        assertThat(keys.unsealWithRecoveryKey(TestFixtures.recoveryKey)).isTrue();

        String unsealKey = keys.enableAutoUnseal();
        keys.seal();
        assertThat(keys.unsealWithUnsealKey(unsealKey)).isTrue();
        String rotated = keys.enableAutoUnseal();
        keys.seal();
        assertThat(keys.unsealWithUnsealKey(unsealKey)).as("old unseal key revoked").isFalse();
        assertThat(keys.unsealWithUnsealKey(rotated)).isTrue();
        keys.disableAutoUnseal();
        keys.seal();
        assertThat(keys.unsealWithUnsealKey(rotated)).isFalse();
        assertThat(keys.unsealWithPassword(MASTER.toCharArray())).isTrue();

        String oldRecovery = TestFixtures.recoveryKey;
        TestFixtures.recoveryKey = keys.regenerateRecoveryKey();
        keys.seal();
        assertThat(keys.unsealWithRecoveryKey(oldRecovery)).isFalse();
        assertThat(keys.unsealWithRecoveryKey(TestFixtures.recoveryKey)).isTrue();

        keys.changeMasterPassword("new-master-password-1".toCharArray());
        assertThat(keys.verifyPassword(MASTER.toCharArray())).isFalse();
        assertThat(keys.verifyPassword("new-master-password-1".toCharArray())).isTrue();
        keys.changeMasterPassword(MASTER.toCharArray());

        assertThat(secrets.get("rotation", "K")).contains("survives");
    }
}
