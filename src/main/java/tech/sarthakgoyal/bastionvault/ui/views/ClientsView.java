package tech.sarthakgoyal.bastionvault.ui.views;

import java.time.Instant;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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
public class ClientsView extends Div {

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
        setWidthFull();

        grid.addComponentColumn(c -> {
            Span id = new Span(c.getClientId());
            id.addClassName("bv-addr");
            Span name = new Span(c.getDisplayName());
            name.addClassName("bv-col-label");
            return new Div(id, name);
        }).setHeader("Client").setAutoWidth(true).setFlexGrow(1);
        grid.addComponentColumn(c -> c.isActive() ? UiSupport.status("Active", "ok") : UiSupport.status("Disabled", ""))
                .setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(this::accessChips).setHeader("Can open").setFlexGrow(2);
        grid.addColumn(c -> clients.apiKeys(c.getClientId()).stream().filter(k -> k.isUsable(Instant.now())).count())
                .setHeader("API keys").setAutoWidth(true);
        grid.addColumn(c -> UiSupport.relative(c.getLastUsedAt())).setHeader("Last token").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setAllRowsVisible(true);

        Div page = UiSupport.page();
        page.add(UiSupport.header("Machine identities",
                        "Clients can open nothing until a rule grants them a group",
                        UiSupport.primary("New client", "plus", e -> openCreate())),
                grid);
        add(page);
        refresh();
    }

    private String admin() {
        return UiSupport.admin(auth);
    }

    private void refresh() {
        grid.setItems(clients.list());
    }

    private Div accessChips(ApiClient client) {
        Div chips = new Div();
        chips.addClassName("bv-chips");
        List<PolicyRule> rules = policies.rulesFor(client.getClientId());
        if (rules.isEmpty()) {
            Span none = new Span("Nothing yet");
            none.addClassName("bv-none");
            chips.add(none);
        }
        for (PolicyRule r : rules) {
            Span chip = new Span(r.getGroupPattern() + " · "
                    + (r.isCanRead() && r.isCanWrite() ? "read, write" : r.isCanRead() ? "read" : "write"));
            chip.addClassName("bv-chip");
            chips.add(chip);
        }
        return chips;
    }

    private Div actions(ApiClient client) {
        Button access = UiSupport.secondary("Access", "key", e -> openAccess(client));
        Button more = UiSupport.iconButton("ellipsis", "More", e -> { });
        ContextMenu menu = new ContextMenu(more);
        menu.setOpenOnClick(true);
        menu.addItem("Rotate secret", e -> confirmRotate(client));
        menu.addItem(client.isActive() ? "Disable" : "Enable", e -> {
            clients.setActive(client.getClientId(), !client.isActive());
            audit.admin(admin(), client.isActive() ? "DISABLE_CLIENT" : "ENABLE_CLIENT", client.getClientId(),
                    Outcome.SUCCESS);
            refresh();
        });
        menu.addItem("Delete", e -> confirmDelete(client)).addClassName("bv-danger-item");
        Div actions = new Div(access, more);
        actions.addClassName("bv-row-actions");
        return actions;
    }

    // ---------------------------------------------------------------- dialogs

    private void openCreate() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New client");
        dialog.setWidth("480px");
        TextField id = new TextField("Client ID");
        id.setPlaceholder("external-secrets");
        id.setHelperText("Lowercase letters, digits, dot, dash or underscore");
        TextField name = new TextField("Display name");
        name.setPlaceholder("External Secrets Operator");
        id.setWidthFull();
        name.setWidthFull();
        Button create = UiSupport.primary("Create client", null, e -> {
            try {
                String secret = clients.create(id.getValue().trim(), name.getValue());
                audit.admin(admin(), "CREATE_CLIENT", id.getValue(), Outcome.SUCCESS);
                dialog.close();
                refresh();
                UiSupport.showOnce("Client secret for " + id.getValue(),
                        "Use it with POST /api/v1/auth/token. The client can open nothing until you add a rule under Access.",
                        secret, null);
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        VerticalLayout body = new VerticalLayout(id, name);
        body.setPadding(false);
        dialog.add(body);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), create);
        dialog.open();
        id.focus();
    }

    private void confirmRotate(ApiClient client) {
        ConfirmDialog d = new ConfirmDialog("Rotate secret for " + client.getClientId() + "?",
                "The old secret and its active tokens stop working now. API keys keep working.", "Rotate", ev -> {
                    String secret = clients.rotateSecret(client.getClientId());
                    audit.admin(admin(), "ROTATE_CLIENT_SECRET", client.getClientId(), Outcome.SUCCESS);
                    UiSupport.showOnce("New client secret", "Update the consumer's configuration.", secret, null);
                });
        d.setCancelable(true);
        d.open();
    }

    private void confirmDelete(ApiClient client) {
        ConfirmDialog d = new ConfirmDialog("Delete " + client.getClientId() + "?",
                "Removes the client, its rules and all its API keys. This cannot be undone.", "Delete", ev -> {
                    clients.delete(client.getClientId());
                    audit.admin(admin(), "DELETE_CLIENT", client.getClientId(), Outcome.SUCCESS);
                    refresh();
                });
        d.setConfirmButtonTheme("error primary");
        d.setCancelable(true);
        d.open();
    }

    private void openAccess(ApiClient client) {
        String clientId = client.getClientId();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Access for " + clientId);
        dialog.setWidth("820px");

        // ---- policy rules
        Grid<PolicyRule> rules = new Grid<>(PolicyRule.class, false);
        rules.addColumn(PolicyRule::getGroupPattern).setHeader("Group pattern").setAutoWidth(true).setFlexGrow(1);
        rules.addColumn(r -> r.isCanRead() ? "Yes" : "No").setHeader("Read");
        rules.addColumn(r -> r.isCanWrite() ? "Yes" : "No").setHeader("Write");
        rules.addComponentColumn(r -> UiSupport.iconButton("trash-2", "Remove rule", e -> {
            policies.removeRule(r.getId());
            audit.admin(admin(), "REMOVE_POLICY", clientId + ":" + r.getGroupPattern(), Outcome.SUCCESS);
            rules.setItems(policies.rulesFor(clientId));
            refresh();
        })).setFlexGrow(0).setAutoWidth(true);
        rules.setAllRowsVisible(true);
        rules.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        rules.setItems(policies.rulesFor(clientId));

        TextField pattern = new TextField("Group pattern");
        pattern.setPlaceholder("affine, app-*, *");
        Checkbox read = new Checkbox("Read", true);
        Checkbox write = new Checkbox("Write", false);
        Button addRule = UiSupport.secondary("Add rule", "plus", e -> {
            try {
                policies.addRule(clientId, pattern.getValue().trim(), read.getValue(), write.getValue());
                audit.admin(admin(), "ADD_POLICY", clientId + ":" + pattern.getValue()
                        + (read.getValue() ? " r" : "") + (write.getValue() ? " w" : ""), Outcome.SUCCESS);
                pattern.clear();
                rules.setItems(policies.rulesFor(clientId));
                refresh();
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        HorizontalLayout ruleForm = new HorizontalLayout(pattern, read, write, addRule);
        ruleForm.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        // ---- API keys
        Grid<ApiKey> keys = new Grid<>(ApiKey.class, false);
        keys.addColumn(ApiKey::getName).setHeader("Name").setAutoWidth(true);
        keys.addComponentColumn(k -> {
            Span s = new Span("bvk_" + k.getKeyId() + "_…");
            s.addClassName("bv-addr");
            return s;
        }).setHeader("Key").setAutoWidth(true);
        keys.addColumn(k -> k.getExpiresAt() == null ? "Never" : UiSupport.DATE_TIME.format(k.getExpiresAt()))
                .setHeader("Expires");
        keys.addColumn(k -> UiSupport.relative(k.getLastUsedAt())).setHeader("Last used");
        keys.addComponentColumn(k -> k.isRevoked() ? UiSupport.status("Revoked", "bad") : UiSupport.status("Active", "ok"))
                .setHeader("Status");
        keys.addComponentColumn(k -> {
            Button revoke = UiSupport.iconButton("x", "Revoke key", e -> {
                clients.revokeApiKey(k.getId());
                audit.admin(admin(), "REVOKE_API_KEY", clientId + ":" + k.getName(), Outcome.SUCCESS);
                keys.setItems(clients.apiKeys(clientId));
                refresh();
            });
            revoke.setEnabled(!k.isRevoked());
            return revoke;
        }).setFlexGrow(0).setAutoWidth(true);
        keys.setAllRowsVisible(true);
        keys.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        keys.setItems(clients.apiKeys(clientId));

        TextField keyName = new TextField("Key name");
        keyName.setPlaceholder("eso-homelab");
        IntegerField days = new IntegerField("Expires in days");
        days.setPlaceholder("Never");
        days.setMin(1);
        Button addKey = UiSupport.secondary("Create API key", "plus", e -> {
            try {
                String token = clients.createApiKey(clientId, keyName.getValue(), days.getValue());
                audit.admin(admin(), "CREATE_API_KEY", clientId + ":" + keyName.getValue(), Outcome.SUCCESS);
                UiSupport.showOnce("API key " + keyName.getValue(),
                        "Send it as Authorization: Bearer <key>. It has the same access as " + clientId + ".",
                        token, null);
                keyName.clear();
                keys.setItems(clients.apiKeys(clientId));
                refresh();
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        HorizontalLayout keyForm = new HorizontalLayout(keyName, days, addKey);
        keyForm.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        Paragraph hint = new Paragraph("* matches any characters and ? one character. No rule means no access.");
        hint.addClassName("muted");
        dialog.add(new VerticalLayout(new H3("Rules"), hint, rules, ruleForm, new H3("API keys"), keys, keyForm));
        dialog.getFooter().add(UiSupport.primary("Done", null, e -> dialog.close()));
        dialog.open();
    }
}
