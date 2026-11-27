package tech.sarthakgoyal.bastionvault.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import tech.sarthakgoyal.bastionvault.TestFixtures;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.service.SetupService;
import tech.sarthakgoyal.bastionvault.ui.views.AuditView;
import tech.sarthakgoyal.bastionvault.ui.views.ClientsView;
import tech.sarthakgoyal.bastionvault.ui.views.LoginView;
import tech.sarthakgoyal.bastionvault.ui.views.SecretsView;
import tech.sarthakgoyal.bastionvault.ui.views.SettingsView;
import tech.sarthakgoyal.bastionvault.ui.views.UnsealView;

/** Renders the real views server-side (no browser) and drives them like a user would. */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class ViewsBrowserlessTest extends SpringBrowserlessTest {

    @Autowired SetupService setup;
    @Autowired KeyManager keys;
    @Autowired SecretService secrets;
    @Autowired ClientService clients;

    @BeforeEach
    void vault() {
        TestFixtures.initializedAndUnsealed(setup, keys);
    }

    private Button button(String caption) {
        return $(Button.class).withCaption(caption).first();
    }

    /** The topmost open dialog (show-once dialogs open after the form dialog closes). */
    private Dialog openDialog() {
        return $(Dialog.class).withCondition(Dialog::isOpened).last();
    }

    @Test
    void sealedVaultForcesTheUnsealPage() {
        keys.seal();
        navigate("", UnsealView.class); // the layout forwards any protected page to /unseal

        // the vault door, not the sidebar card (which also has an "Unseal" button that only navigates)
        PasswordField secret = $view(PasswordField.class).single();
        Button unsealButton = $view(Button.class).withCaption("Unseal").single();
        secret.setValue("definitely-wrong");
        test(unsealButton).click();
        assertThat(keys.isSealed()).isTrue();

        secret.setValue(TestFixtures.MASTER);
        test(unsealButton).click();
        assertThat(keys.isSealed()).isFalse();
        assertThat(getCurrentView()).isInstanceOf(SecretsView.class);
    }

    @Test
    void saveAndRevealASecret() {
        navigate(SecretsView.class);
        test(button("New secret")).click();
        Dialog editor = openDialog();
        $(TextField.class, editor).withCaption("Group").single().setValue("ui-group");
        $(TextField.class, editor).withCaption("Key").single().setValue("UI_KEY");
        $(TextArea.class, editor).withCaption("Value").single().setValue("multi\nline");
        test($(Button.class, editor).withCaption("Save secret").single()).click();
        assertThat(secrets.get("ui-group", "UI_KEY")).contains("multi\nline");

        // Searching opens the matching group box; its rows have icon buttons labelled for screen readers.
        $(TextField.class).withPropertyValue(TextField::getPlaceholder, "Search").single().setValue("ui-group");
        test($(Button.class).withAttribute("aria-label", "Reveal").first()).click();
        Dialog dialog = openDialog();
        assertThat(dialog.isOpened()).isTrue();
        assertThat($(TextArea.class, dialog).single().getValue()).isEqualTo("multi\nline");
    }

    @Test
    void importEnvFileThroughTheDialog() {
        secrets.put("ui-env", "KEEP", "original", "test");
        navigate(SecretsView.class);
        test(button("Import file")).click();
        Dialog dialog = $(Dialog.class).first();
        $(TextField.class, dialog).withCaption("Group").single().setValue("ui-env");
        String env = "KEEP=changed\nNEW_ONE=hello\nexport SECOND=\"two words\"\n";
        test($(com.vaadin.flow.component.upload.Upload.class, dialog).single())
                .upload("app.env", "text/plain", env.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(test($(Grid.class, dialog).single()).size()).isEqualTo(3);
        Button importButton = $(Button.class, dialog).withCaptionContaining("Import ").single();
        assertThat(importButton.getText()).isEqualTo("Import 2 secrets"); // KEEP is skipped by default
        test(importButton).click();

        assertThat(secrets.get("ui-env", "NEW_ONE")).contains("hello");
        assertThat(secrets.get("ui-env", "SECOND")).contains("two words");
        assertThat(secrets.get("ui-env", "KEEP")).contains("original");
    }

    @Test
    void importYamlFileThroughTheDialog() {
        navigate(SecretsView.class);
        test(button("Import file")).click();
        Dialog dialog = $(Dialog.class).first();
        $(TextField.class, dialog).withCaption("Group").single().setValue("ui-yaml");
        String yaml = "FLAG: yes\nCERT: |\n  a\n  b\n";
        test($(com.vaadin.flow.component.upload.Upload.class, dialog).single())
                .upload("config.yaml", "application/yaml", yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(test($(Grid.class, dialog).single()).size()).isEqualTo(2);
        test($(Button.class, dialog).withCaptionContaining("Import ").single()).click();
        assertThat(secrets.get("ui-yaml", "FLAG")).contains("yes");
        assertThat(secrets.get("ui-yaml", "CERT")).contains("a\nb\n");
    }

    @Test
    void exportEverythingNeedsTheMasterPassword() {
        navigate(SettingsView.class);
        test(button("Export everything")).click();
        assertThat($(Dialog.class).all()).noneMatch(Dialog::isOpened);
        $(PasswordField.class).withCaption("Master password").single().setValue(TestFixtures.MASTER);
        test(button("Export everything")).click();
        Dialog dialog = openDialog();
        assertThat($(com.vaadin.flow.component.html.Anchor.class, dialog).all()).hasSize(2);
    }

    @Test
    void createClientShowsTheSecretOnce() {
        navigate(ClientsView.class);
        test(button("New client")).click();
        Dialog form = openDialog();
        $(TextField.class, form).withCaption("Client ID").single().setValue("ui-client");
        $(TextField.class, form).withCaption("Display name").single().setValue("From the UI");
        test($(Button.class, form).withCaption("Create client").single()).click();
        assertThat(clients.list()).anyMatch(c -> c.getClientId().equals("ui-client"));
        Dialog dialog = openDialog();
        assertThat($(TextArea.class, dialog).single().getValue()).hasSizeGreaterThan(30);
        assertThat($(Button.class, dialog).withCaption("Done").single().isEnabled()).isFalse();
    }

    @Test
    void enableAutoUnsealFromSettings() {
        navigate(SettingsView.class);
        test(button("Generate unseal key")).click();
        test($(ConfirmDialog.class).first()).confirm();
        assertThat(keys.hasAutoUnseal()).isTrue();
        String key = $(TextArea.class, openDialog()).single().getValue();
        assertThat(key).startsWith("bvu_");
        keys.seal();
        assertThat(keys.unsealWithUnsealKey(key)).isTrue();
        keys.disableAutoUnseal();
    }

    @Test
    void themeToggleIsInTheSidebar() {
        navigate(SecretsView.class);
        assertThat($(Button.class).withAttribute("aria-label", "Switch theme").all()).hasSize(1);
    }

    @Test
    void auditViewRenders() {
        navigate(AuditView.class);
        assertThat($(Grid.class).single()).isNotNull();
    }

    @Test
    @WithAnonymousUser
    void anonymousUsersGetTheLoginForm() {
        navigate(LoginView.class);
        assertThat($(LoginForm.class).single()).isNotNull();
        assertThat($(Button.class).withAttribute("aria-label", "Switch theme").all()).hasSize(1);
    }
}
