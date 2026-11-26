package tech.sarthakgoyal.bastionvault.ui.views;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.domain.ApiClient;
import tech.sarthakgoyal.bastionvault.domain.ApiKey;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.domain.PolicyRule;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.PolicyService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** Machine identities, their policy rules (2B) and API keys (3B). */
@Route(value = "clients", layout = MainLayout.class)
@PageTitle("Machine identities | BastionVault")
@RolesAllowed("ADMIN")
public class ClientsView extends VerticalLayout {

    private final ClientService clients;
    private final PolicyService policies;
    private final AuditService audit;
    private final AuthenticationContext auth;
    private final Grid<ApiClient> grid = new Grid<>(ApiClient.class, false);

    public ClientsView(ClientService clients, PolicyService policies, AuditService audit, AuthenticationContext auth) {
        this.clients = clients;
        this.policies = policies;
        this.audit = audit;
        this.auth = auth;
        setSizeFull();

        TextField id = new TextField("Client ID");
        id.setPlaceholder("e.g. external-secrets");
        TextField name = new TextField("Display name");
        Button create = new Button("Create client", VaadinIcon.PLUS.create(), e -> {
            try {
                String secret = clients.create(id.getValue().trim(), name.getValue());
                audit.admin(admin(), "CREATE_CLIENT", id.getValue(), Outcome.SUCCESS);
                UiSupport.showOnce("Client secret for " + id.getValue(),
                        "Use it with POST /api/v1/auth/token. The client has no access until you add a policy rule.",
                        secret, null);
                id.clear();
                name.clear();
                refresh();
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout toolbar = new HorizontalLayout(id, name, create);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addColumn(ApiClient::getClientId).setHeader("Client ID").setAutoWidth(true);
        grid.addColumn(ApiClient::getDisplayName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(c -> c.isActive() ? "active" : "disabled").setHeader("Status");
        grid.addColumn(c -> c.getLastUsedAt() == null ? "never" : SecretsView.TIME.format(c.getLastUsedAt()))
                .setHeader("Last token").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader("Actions").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(true);

        add(new H2("Machine identities"),
                new Paragraph("Each client is denied everything until a policy rule grants it a group. "
                        + "Use short-lived tokens for scripts and API keys for long-running consumers (External Secrets)."),
                toolbar, grid);
        refresh();
    }

    private String admin() {
        return UiSupport.admin(auth);
    }

    private void refresh() {
        grid.setItems(clients.list());
    }

    private HorizontalLayout actions(ApiClient client) {
        Button access = new Button("Access & keys", VaadinIcon.KEY.create(), e -> openAccess(client));
        Button rotate = new Button("Rotate secret", VaadinIcon.REFRESH.create(), e -> {
            ConfirmDialog d = new ConfirmDialog("Rotate secret for " + client.getClientId() + "?",
                    "The old secret and its active tokens stop working immediately. API keys are not affected.",
                    "Rotate", ev -> {
                        String secret = clients.rotateSecret(client.getClientId());
                        audit.admin(admin(), "ROTATE_CLIENT_SECRET", client.getClientId(), Outcome.SUCCESS);
                        UiSupport.showOnce("New client secret", "Update the consumer's configuration.", secret, null);
                    });
            d.setCancelable(true);
            d.open();
        });
        Button toggle = new Button(client.isActive() ? "Disable" : "Enable", e -> {
            clients.setActive(client.getClientId(), !client.isActive());
            audit.admin(admin(), client.isActive() ? "DISABLE_CLIENT" : "ENABLE_CLIENT", client.getClientId(),
                    Outcome.SUCCESS);
            refresh();
        });
        Button delete = new Button("Delete", VaadinIcon.TRASH.create(), e -> {
            ConfirmDialog d = new ConfirmDialog("Delete " + client.getClientId() + "?",
                    "Removes the client, its policy rules and all its API keys.", "Delete", ev -> {
                        clients.delete(client.getClientId());
                        audit.admin(admin(), "DELETE_CLIENT", client.getClientId(), Outcome.SUCCESS);
                        refresh();
                    });
            d.setConfirmButtonTheme("error primary");
            d.setCancelable(true);
            d.open();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        for (Button b : List.of(access, rotate, toggle, delete)) {
            b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        }
        return new HorizontalLayout(access, rotate, toggle, delete);
    }

    private void openAccess(ApiClient client) {
        String clientId = client.getClientId();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Access for " + clientId);
        dialog.setWidth("820px");

        // ---- policy rules
        Grid<PolicyRule> rules = new Grid<>(PolicyRule.class, false);
        rules.addColumn(PolicyRule::getGroupPattern).setHeader("Group pattern").setAutoWidth(true);
        rules.addColumn(r -> r.isCanRead() ? "yes" : "").setHeader("Read");
        rules.addColumn(r -> r.isCanWrite() ? "yes" : "").setHeader("Write");
        rules.addComponentColumn(r -> {
            Button remove = new Button(VaadinIcon.TRASH.create(), e -> {
                policies.removeRule(r.getId());
                audit.admin(admin(), "REMOVE_POLICY", clientId + ":" + r.getGroupPattern(), Outcome.SUCCESS);
                rules.setItems(policies.rulesFor(clientId));
            });
            remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            return remove;
        });
        rules.setAllRowsVisible(true);
        rules.addThemeVariants(GridVariant.LUMO_COMPACT);
        rules.setItems(policies.rulesFor(clientId));

        TextField pattern = new TextField("Group pattern");
        pattern.setPlaceholder("affine, app-*, *");
        Checkbox read = new Checkbox("Read", true);
        Checkbox write = new Checkbox("Write", false);
        Button addRule = new Button("Add rule", e -> {
            try {
                policies.addRule(clientId, pattern.getValue().trim(), read.getValue(), write.getValue());
                audit.admin(admin(), "ADD_POLICY", clientId + ":" + pattern.getValue()
                        + (read.getValue() ? " r" : "") + (write.getValue() ? " w" : ""), Outcome.SUCCESS);
                pattern.clear();
                rules.setItems(policies.rulesFor(clientId));
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        HorizontalLayout ruleForm = new HorizontalLayout(pattern, read, write, addRule);
        ruleForm.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        // ---- API keys
        Grid<ApiKey> keys = new Grid<>(ApiKey.class, false);
        keys.addColumn(ApiKey::getName).setHeader("Name").setAutoWidth(true);
        keys.addColumn(k -> "bvk_" + k.getKeyId() + "_…").setHeader("Key").setAutoWidth(true);
        keys.addColumn(k -> k.getExpiresAt() == null ? "never" : SecretsView.TIME.format(k.getExpiresAt()))
                .setHeader("Expires");
        keys.addColumn(k -> k.getLastUsedAt() == null ? "never" : SecretsView.TIME.format(k.getLastUsedAt()))
                .setHeader("Last used");
        keys.addColumn(k -> k.isRevoked() ? "revoked" : "active").setHeader("Status");
        keys.addComponentColumn(k -> {
            Button revoke = new Button("Revoke", e -> {
                clients.revokeApiKey(k.getId());
                audit.admin(admin(), "REVOKE_API_KEY", clientId + ":" + k.getName(), Outcome.SUCCESS);
                keys.setItems(clients.apiKeys(clientId));
            });
            revoke.setEnabled(!k.isRevoked());
            revoke.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            return revoke;
        });
        keys.setAllRowsVisible(true);
        keys.addThemeVariants(GridVariant.LUMO_COMPACT);
        keys.setItems(clients.apiKeys(clientId));

        TextField keyName = new TextField("Key name");
        keyName.setPlaceholder("e.g. eso-homelab");
        IntegerField days = new IntegerField("Expires in days");
        days.setPlaceholder("never");
        days.setMin(1);
        Button addKey = new Button("Create API key", e -> {
            try {
                String token = clients.createApiKey(clientId, keyName.getValue(), days.getValue());
                audit.admin(admin(), "CREATE_API_KEY", clientId + ":" + keyName.getValue(), Outcome.SUCCESS);
                UiSupport.showOnce("API key " + keyName.getValue(),
                        "Send it as 'Authorization: Bearer <key>'. It has the same access as " + clientId + ".",
                        token, null);
                keyName.clear();
                keys.setItems(clients.apiKeys(clientId));
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        HorizontalLayout keyForm = new HorizontalLayout(keyName, days, addKey);
        keyForm.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        Button close = new Button("Close", e -> dialog.close());
        dialog.add(new VerticalLayout(
                new H4("Policy rules"),
                new Paragraph("* matches any characters, ? one character. No rule = no access."),
                rules, ruleForm, new H4("API keys"), keys, keyForm));
        dialog.getFooter().add(close);
        dialog.open();
    }
}
