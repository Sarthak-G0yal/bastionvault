package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.security.crypto.password.PasswordEncoder;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.model.ApiClientDocument;
import tech.sarthakgoyal.bastionvault.model.AuditLogDocument;
import tech.sarthakgoyal.bastionvault.repository.ApiClientRepository;
import tech.sarthakgoyal.bastionvault.repository.AuditLogRepository;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;
import java.security.SecureRandom;
import java.util.Base64;

@Route(value = "clients", layout = MainLayout.class)
@PageTitle("Machine Identities | Bastion")
public class MachineIdentitiesView extends VerticalLayout implements BeforeEnterObserver {

    private final ApiClientRepository clientRepository;
    private final AuditLogRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final VaultSessionManager sessionManager;

    private final Grid<ApiClientDocument> clientGrid = new Grid<>(ApiClientDocument.class, false);
    private final Grid<AuditLogDocument> auditGrid = new Grid<>(AuditLogDocument.class, false);

    public MachineIdentitiesView(VaultSessionManager sessionManager, ApiClientRepository clientRepository,
            AuditLogRepository auditRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2("Machine Identities & Access Control"));
        add(new Paragraph("Manage API access for your infrastructure automation tools (Terraform, Ansible, Docker)."));

        // 1. Client Creation Toolbar
        HorizontalLayout toolbar = createToolbar();
        add(toolbar);

        // 2. The Active Clients Grid
        add(new H3("Active API Clients"));
        setupClientGrid();
        add(clientGrid);

        // 3. The Audit Logs Grid
        add(new H3("Recent API Activity"));
        setupAuditGrid();
        add(auditGrid);

        // Only pull data if securely unlocked
        if (sessionManager.isUnlocked()) {
            refreshGrids();
        }
    }

    private HorizontalLayout createToolbar() {
        TextField clientIdField = new TextField("Client ID (e.g., docker-host)");
        clientIdField.setWidth("300px");

        TextField clientNameField = new TextField("Friendly Name");
        clientNameField.setWidth("300px");

        Button createButton = new Button("Generate New Client", new Icon(VaadinIcon.PLUS), e -> {
            if (clientIdField.isEmpty() || clientNameField.isEmpty()) {
                Notification.show("Please fill out both fields.");
                return;
            }

            if (clientRepository.existsById(clientIdField.getValue())) {
                Notification.show("Client ID already exists!");
                return;
            }

            // Generate a secure 32-character random string for the password
            byte[] randomBytes = new byte[24];
            new SecureRandom().nextBytes(randomBytes);
            String rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

            // Save the BCrypt hash to the database
            ApiClientDocument newClient = new ApiClientDocument();
            newClient.setClientId(clientIdField.getValue());
            newClient.setClientName(clientNameField.getValue());
            newClient.setClientSecretHash(passwordEncoder.encode(rawSecret));
            clientRepository.save(newClient);

            // Show the raw secret to the user EXACTLY ONCE
            showSecretDialog(clientIdField.getValue(), rawSecret);

            clientIdField.clear();
            clientNameField.clear();
            refreshGrids();
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.getStyle().set("margin-top", "auto"); // Align with text fields

        HorizontalLayout toolbar = new HorizontalLayout(clientIdField, clientNameField, createButton);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
        return toolbar;
    }

    private void setupClientGrid() {
        clientGrid.addColumn(ApiClientDocument::getClientId).setHeader("Client ID").setAutoWidth(true);
        clientGrid.addColumn(ApiClientDocument::getClientName).setHeader("Name").setAutoWidth(true);
        clientGrid.addColumn(ApiClientDocument::isActive).setHeader("Active").setAutoWidth(true);
        clientGrid.addColumn(ApiClientDocument::getCreatedAt).setHeader("Created At").setAutoWidth(true);

        clientGrid.addComponentColumn(client -> {
            Button revokeBtn = new Button("Revoke", VaadinIcon.TRASH.create());
            revokeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            revokeBtn.addClickListener(e -> {
                clientRepository.delete(client);
                refreshGrids();
                Notification.show("Client revoked successfully.");
            });
            return revokeBtn;
        }).setHeader("Actions");
    }

    private void setupAuditGrid() {
        auditGrid.addColumn(AuditLogDocument::getTimestamp).setHeader("Timestamp").setAutoWidth(true);
        auditGrid.addColumn(AuditLogDocument::getClientId).setHeader("Client ID").setAutoWidth(true);
        auditGrid.addColumn(AuditLogDocument::getAction).setHeader("Action").setAutoWidth(true);
        auditGrid.addColumn(AuditLogDocument::getTargetResource).setHeader("Target").setAutoWidth(true);
        auditGrid.addColumn(AuditLogDocument::getStatus).setHeader("Status").setAutoWidth(true);
    }

    private void refreshGrids() {
        clientGrid.setItems(clientRepository.findAll());
        // In production, you would paginate this. For now, pull the latest.
        auditGrid.setItems(auditRepository.findAll());
    }

    private void showSecretDialog(String clientId, String rawSecret) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Client Created Successfully!");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.add(new Paragraph("WARNING: This is the ONLY time you will see this Client Secret. Copy it now."));

        TextField secretField = new TextField("Client Secret");
        secretField.setValue(rawSecret);
        secretField.setReadOnly(true);
        secretField.setWidthFull();

        dialogLayout.add(secretField);
        dialog.add(dialogLayout);

        Button closeButton = new Button("I have copied it", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(closeButton);

        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.open();
    }

    // THE FIX: Secure Routing Guard
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!sessionManager.isUnlocked()) {
            event.forwardTo(UnlockView.class);
        }
    }
}