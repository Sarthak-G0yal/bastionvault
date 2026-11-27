package tech.sarthakgoyal.bastionvault.ui.views;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AdminAccountService;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.LegacyImportService;
import tech.sarthakgoyal.bastionvault.service.Names;
import tech.sarthakgoyal.bastionvault.service.SecretCodec;
import tech.sarthakgoyal.bastionvault.service.SecretFormat;
import tech.sarthakgoyal.bastionvault.service.SecretImportService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.service.UnsealService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** Export/import, key management (auto-unseal, recovery key, master password), login password, legacy import. */
@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | BastionVault")
@RolesAllowed("ADMIN")
public class SettingsView extends Div {

    private final KeyManager keys;
    private final UnsealService unseal;
    private final AuditService audit;
    private final AuthenticationContext auth;
    private final SecretService secrets;
    private final SecretImportService importer;
    private final SecretCodec codec;
    private final Span autoUnsealStatus = new Span();

    public SettingsView(KeyManager keys, UnsealService unseal, AdminAccountService accounts, AuditService audit,
            LegacyImportService legacy, AuthenticationContext auth, SecretService secrets,
            SecretImportService importer, SecretCodec codec) {
        this.keys = keys;
        this.unseal = unseal;
        this.audit = audit;
        this.auth = auth;
        this.secrets = secrets;
        this.importer = importer;
        this.codec = codec;
        setWidthFull();

        Div grid = new Div(backupSection(), autoUnsealSection(), recoverySection(), masterPasswordSection(),
                loginPasswordSection(accounts), legacySection(legacy));
        grid.addClassName("bv-settings");
        Div page = UiSupport.page();
        page.add(UiSupport.header("Settings", "Keys, backups and your sign-in"), grid);
        add(page);
        updateStatus();
    }

    private String admin() {
        return UiSupport.admin(auth);
    }

    private void updateStatus() {
        boolean on = keys.hasAutoUnseal();
        autoUnsealStatus.setText(on ? "On" : "Off");
        autoUnsealStatus.setClassName(on ? "bv-status warn" : "bv-status");
    }

    private Div section(String title, String text, Component... content) {
        H2 h = new H2(title);
        h.addClassName("bv-section-title");
        Div card = UiSupport.card(h, new Paragraph(text));
        card.add(content);
        return card;
    }

    private Div backupSection() {
        PasswordField master = password("Master password");
        Button export = UiSupport.secondary("Export everything", "download", e -> {
            if (checkMaster(master)) {
                openExportDialog();
            }
        });
        Button importAll = UiSupport.secondary("Import vault file", "upload",
                e -> new VaultImportDialog(secrets, importer, codec, audit, admin()).open());
        return section("Export and import everything",
                "A plaintext JSON or YAML file of every group, for moving secrets between instances. Routine "
                        + "backups should copy PostgreSQL instead, which only holds ciphertext.",
                master, row(export, importAll));
    }

    /** Download links exist only while this dialog is open. */
    private void openExportDialog() {
        String admin = admin();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Export everything");
        dialog.setWidth("480px");
        Div links = new Div();
        links.addClassName("bv-actions");
        for (SecretFormat format : List.of(SecretFormat.JSON, SecretFormat.YAML)) {
            Anchor link = new Anchor(DownloadHandler.fromInputStream(event -> {
                byte[] body = codec.writeVault(secrets.exportAll(), format).getBytes(StandardCharsets.UTF_8);
                audit.admin(admin, "EXPORT_ALL_" + format.name(), null, Outcome.SUCCESS);
                return new DownloadResponse(new ByteArrayInputStream(body),
                        "bastionvault-" + LocalDate.now() + format.extension(), format.contentType(), body.length);
            }), "Download " + format.label());
            link.getElement().setAttribute("download", true);
            link.addClassName("export-link");
            links.add(link);
        }
        Paragraph warning = new Paragraph("The file holds every secret in plain text. Encrypt it (SOPS, a password "
                + "manager) and delete the download when you are done.");
        warning.addClassName("muted");
        dialog.add(new VerticalLayout(warning, links));
        dialog.getFooter().add(UiSupport.primary("Close", null, e -> dialog.close()));
        dialog.open();
    }

    private Div autoUnsealSection() {
        Button enable = UiSupport.secondary("Generate unseal key", "key-round", e -> confirm(
                "Generate a new unseal key?",
                "Any previous unseal key stops working. Update the Kubernetes Secret before the next restart.", () -> {
                    String key = keys.enableAutoUnseal();
                    audit.admin(admin(), "ENABLE_AUTO_UNSEAL", null, Outcome.SUCCESS);
                    updateStatus();
                    UiSupport.showOnce("Unseal key",
                            "Store it as a Kubernetes Secret file and point VAULT_UNSEAL_KEY_FILE at it.", key, null);
                }));
        Button disable = new Button("Turn off", e -> confirm("Turn off auto-unseal?",
                "After the next restart the vault stays sealed until you unseal it here.", () -> {
                    keys.disableAutoUnseal();
                    audit.admin(admin(), "DISABLE_AUTO_UNSEAL", null, Outcome.SUCCESS);
                    updateStatus();
                }));
        disable.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        Span label = new Span("Status");
        label.addClassName("muted");
        return section("Auto-unseal",
                "Unseals the vault on start from VAULT_UNSEAL_KEY_FILE, a mounted Kubernetes Secret kept in git with "
                        + "SOPS. Anyone who controls the cluster can then read the vault. The master password and "
                        + "recovery key keep working.",
                row(label, autoUnsealStatus), row(enable, disable));
    }

    private Div recoverySection() {
        PasswordField master = password("Current master password");
        Button regenerate = UiSupport.secondary("Regenerate recovery key", "refresh-cw", e -> {
            if (!checkMaster(master)) {
                return;
            }
            String key = keys.regenerateRecoveryKey();
            audit.admin(admin(), "REGENERATE_RECOVERY_KEY", null, Outcome.SUCCESS);
            UiSupport.showOnce("New recovery key", "Store it offline. The old recovery key no longer works.", key, null);
        });
        return section("Recovery key", "Unseals the vault if the master password is lost. Regenerating it "
                + "invalidates the old one.", master, row(regenerate));
    }

    private Div masterPasswordSection() {
        PasswordField current = password("Current master password");
        PasswordField next = password("New master password");
        PasswordField repeat = password("Repeat new master password");
        Button change = UiSupport.secondary("Change master password", null, e -> {
            if (!next.getValue().equals(repeat.getValue())) {
                UiSupport.error("The new passwords do not match");
                return;
            }
            try {
                Names.password(next.getValue(), "Master password");
            } catch (IllegalArgumentException ex) {
                UiSupport.error(ex.getMessage());
                return;
            }
            if (!checkMaster(current)) {
                return;
            }
            keys.changeMasterPassword(next.getValue().toCharArray());
            audit.admin(admin(), "CHANGE_MASTER_PASSWORD", null, Outcome.SUCCESS);
            next.clear();
            repeat.clear();
            UiSupport.success("Master password changed");
        });
        return section("Master password", "Re-wraps the data key with the new password. Secrets are not "
                + "re-encrypted.", current, next, repeat, row(change));
    }

    private Div loginPasswordSection(AdminAccountService accounts) {
        PasswordField current = password("Current login password");
        PasswordField next = password("New login password");
        PasswordField repeat = password("Repeat new login password");
        Button change = UiSupport.secondary("Change login password", null, e -> {
            if (!next.getValue().equals(repeat.getValue())) {
                UiSupport.error("The new passwords do not match");
                return;
            }
            try {
                accounts.changePassword(admin(), current.getValue(), next.getValue());
                current.clear();
                next.clear();
                repeat.clear();
                UiSupport.success("Login password changed");
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        return section("Login password", "The password for signing in to this page as " + admin() + ".",
                current, next, repeat, row(change));
    }

    private Div legacySection(LegacyImportService legacy) {
        TextArea export = new TextArea("mongoexport output");
        export.setWidthFull();
        export.setMaxHeight("12em");
        export.addClassName("mono");
        PasswordField oldPassword = password("Old master password");
        PasswordField oldSalt = password("Old VAULT_SALT");
        Checkbox overwrite = new Checkbox("Overwrite secrets that already exist");
        Button run = UiSupport.secondary("Import", "upload", e -> {
            try {
                var result = legacy.importMongoExport(export.getValue(), oldPassword.getValue(), oldSalt.getValue(),
                        overwrite.getValue(), "admin:" + admin());
                export.clear();
                oldPassword.clear();
                oldSalt.clear();
                if (result.errors().isEmpty()) {
                    UiSupport.success("Imported " + result.imported() + ", skipped " + result.skippedExisting());
                } else {
                    UiSupport.error("Imported " + result.imported() + ", skipped " + result.skippedExisting()
                            + ", failed " + result.errors().size() + ": " + String.join("; ", result.errors()));
                }
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        return section("Import from BastionVault 0.1",
                "Paste the output of mongoexport --collection secrets --jsonArray and enter the old master password "
                        + "and VAULT_SALT. Values are re-encrypted with AES-GCM.",
                export, oldPassword, oldSalt, overwrite, row(run));
    }

    // ---------------------------------------------------------------- helpers

    private static Div row(Component... children) {
        Div row = new Div(children);
        row.addClassName("bv-actions");
        return row;
    }

    private boolean checkMaster(PasswordField field) {
        if (field.isEmpty()) {
            UiSupport.error("Enter the master password first");
            return false;
        }
        try {
            boolean ok = unseal.verifyPassword(admin(), field.getValue());
            field.clear();
            if (!ok) {
                UiSupport.error("Master password is wrong");
            }
            return ok;
        } catch (RuntimeException ex) {
            UiSupport.error(ex.getMessage());
            return false;
        }
    }

    private PasswordField password(String label) {
        PasswordField field = new PasswordField(label);
        field.setWidthFull();
        UiSupport.noAutofill(field);
        return field;
    }

    private void confirm(String title, String text, Runnable action) {
        ConfirmDialog dialog = new ConfirmDialog(title, text, "Continue", e -> action.run());
        dialog.setCancelable(true);
        dialog.open();
    }
}
