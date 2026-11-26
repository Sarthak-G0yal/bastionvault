package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AdminAccountService;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.LegacyImportService;
import tech.sarthakgoyal.bastionvault.service.Names;
import tech.sarthakgoyal.bastionvault.service.UnsealService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** Key management (auto-unseal, recovery key, master password), login password, legacy import. */
@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | BastionVault")
@RolesAllowed("ADMIN")
public class SettingsView extends VerticalLayout {

    private final KeyManager keys;
    private final UnsealService unseal;
    private final AuditService audit;
    private final AuthenticationContext auth;
    private final Span autoUnsealStatus = new Span();

    public SettingsView(KeyManager keys, UnsealService unseal, AdminAccountService accounts, AuditService audit,
            LegacyImportService legacy, AuthenticationContext auth) {
        this.keys = keys;
        this.unseal = unseal;
        this.audit = audit;
        this.auth = auth;
        setMaxWidth("900px");

        add(new H2("Settings"));
        add(autoUnsealSection(), recoverySection(), masterPasswordSection(), loginPasswordSection(accounts),
                legacySection(legacy));
        updateStatus();
    }

    private String admin() {
        return UiSupport.admin(auth);
    }

    private void updateStatus() {
        boolean on = keys.hasAutoUnseal();
        autoUnsealStatus.setText(on ? "enabled" : "disabled");
        autoUnsealStatus.getElement().getThemeList().clear();
        autoUnsealStatus.getElement().getThemeList().add(on ? "badge success" : "badge contrast");
    }

    private VerticalLayout section(String title, String text) {
        VerticalLayout s = new VerticalLayout(new H3(title), new Paragraph(text));
        s.addClassName("glass-panel");
        s.setWidthFull();
        return s;
    }

    private VerticalLayout autoUnsealSection() {
        VerticalLayout s = section("Auto-unseal",
                "With auto-unseal the vault unseals itself on start from VAULT_UNSEAL_KEY_FILE (a mounted "
                        + "Kubernetes Secret, kept in git with SOPS). Anyone who controls the cluster can then read "
                        + "the vault. The master password and recovery key keep working.");
        Button enable = new Button("Generate new unseal key", e -> confirm("Generate a new unseal key?",
                "Any previous unseal key stops working. Update the Kubernetes Secret before the next restart.", () -> {
                    String key = keys.enableAutoUnseal();
                    audit.admin(admin(), "ENABLE_AUTO_UNSEAL", null, Outcome.SUCCESS);
                    updateStatus();
                    UiSupport.showOnce("Unseal key",
                            "Store it as a Kubernetes Secret file and point VAULT_UNSEAL_KEY_FILE at it.", key, null);
                }));
        enable.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button disable = new Button("Disable", e -> confirm("Disable auto-unseal?",
                "After the next restart the vault stays sealed until you unseal it here.", () -> {
                    keys.disableAutoUnseal();
                    audit.admin(admin(), "DISABLE_AUTO_UNSEAL", null, Outcome.SUCCESS);
                    updateStatus();
                }));
        disable.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        s.add(new HorizontalLayout(new Span("Status:"), autoUnsealStatus), new HorizontalLayout(enable, disable));
        return s;
    }

    private VerticalLayout recoverySection() {
        VerticalLayout s = section("Recovery key",
                "Unseals the vault if the master password is lost. Regenerating it invalidates the old one.");
        PasswordField master = password("Current master password");
        Button regenerate = new Button("Regenerate recovery key", e -> {
            if (!checkMaster(master)) {
                return;
            }
            String key = keys.regenerateRecoveryKey();
            audit.admin(admin(), "REGENERATE_RECOVERY_KEY", null, Outcome.SUCCESS);
            UiSupport.showOnce("New recovery key", "Store it offline. The old recovery key no longer works.", key, null);
        });
        s.add(master, regenerate);
        return s;
    }

    private VerticalLayout masterPasswordSection() {
        VerticalLayout s = section("Master password",
                "Re-wraps the data key with the new password. Secrets are not re-encrypted.");
        PasswordField current = password("Current master password");
        PasswordField next = password("New master password");
        PasswordField repeat = password("Repeat new master password");
        Button change = new Button("Change master password", e -> {
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
        s.add(current, next, repeat, change);
        return s;
    }

    private VerticalLayout loginPasswordSection(AdminAccountService accounts) {
        VerticalLayout s = section("Login password", "The password for signing in to this UI as " + admin() + ".");
        PasswordField current = password("Current login password");
        PasswordField next = password("New login password");
        PasswordField repeat = password("Repeat new login password");
        Button change = new Button("Change login password", e -> {
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
        s.add(current, next, repeat, change);
        return s;
    }

    private VerticalLayout legacySection(LegacyImportService legacy) {
        VerticalLayout s = section("Import from BastionVault 0.1 (MongoDB)",
                "Run: mongoexport --uri \"$MONGO_URI\" --collection secrets --jsonArray, paste the output, and "
                        + "enter the old master password and old VAULT_SALT. Values are re-encrypted with AES-GCM.");
        TextArea export = new TextArea("mongoexport output");
        export.setWidthFull();
        export.setMaxHeight("14em");
        PasswordField oldPassword = password("Old master password");
        PasswordField oldSalt = password("Old VAULT_SALT");
        Checkbox overwrite = new Checkbox("Overwrite secrets that already exist");
        Button run = new Button("Import", e -> {
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
        s.add(export, oldPassword, oldSalt, overwrite, run);
        return s;
    }

    private boolean checkMaster(PasswordField field) {
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
        field.setWidth("360px");
        UiSupport.noAutofill(field);
        return field;
    }

    private void confirm(String title, String text, Runnable action) {
        ConfirmDialog dialog = new ConfirmDialog(title, text, "Continue", e -> action.run());
        dialog.setCancelable(true);
        dialog.open();
    }
}
