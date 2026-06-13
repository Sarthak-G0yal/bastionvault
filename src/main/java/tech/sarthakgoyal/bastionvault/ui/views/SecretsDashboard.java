package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.model.SecretDocument;
import tech.sarthakgoyal.bastionvault.repository.AdminProfileRepository;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.ui.MainLayout;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | Bastion")
public class SecretsDashboard extends VerticalLayout implements BeforeEnterObserver {

    private final VaultSessionManager sessionManager;
    private final SecretService secretService;
    private final SecretRepository secretRepository;
    private final AdminProfileRepository adminRepository;
    private final Grid<SecretDocument> grid = new Grid<>(SecretDocument.class, false);

    public SecretsDashboard(VaultSessionManager sessionManager, SecretService secretService,
            SecretRepository secretRepository, AdminProfileRepository adminRepository) {
        this.sessionManager = sessionManager;
        this.secretService = secretService;
        this.secretRepository = secretRepository;
        this.adminRepository = adminRepository;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header Section (Title & Status & Lock Button)
        H2 heading = new H2("Active Secrets Engine");
        heading.getStyle().set("margin", "0");

        Span statusBadge = new Span("Decrypted Status: Active");
        statusBadge.getElement().getThemeList().add("badge success primary");

        VerticalLayout titleLayout = new VerticalLayout(heading, statusBadge);
        titleLayout.setPadding(false);
        titleLayout.setSpacing(false);

        Button lockButton = new Button("Lock Vault Storage", e -> {
            sessionManager.lockVault();
            getUI().ifPresent(ui -> ui.navigate("unlock"));
        });
        lockButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout topBar = new HorizontalLayout(titleLayout, lockButton);
        topBar.setWidthFull();
        topBar.setAlignItems(Alignment.CENTER);
        topBar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        // Data Table Configuration
        grid.addColumn(SecretDocument::getKey).setHeader("Secret Target Key").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(SecretDocument::getLastUpdated).setHeader("Last Cryptographic Modification").setAutoWidth(true);
        grid.addClassName("glass-panel");
        grid.setSizeFull();

        grid.addComponentColumn(secret -> {
            Button deleteBtn = new Button("Revoke", ev -> {
                secretRepository.delete(secret);
                refreshGrid();
                Notification.show("Revoked " + secret.getKey(), 2000, Notification.Position.BOTTOM_END);
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return deleteBtn;
        }).setHeader("Actions").setAutoWidth(true);

        // Control Entry Form Layout
        TextField keyField = new TextField("Target Key Mapping");
        keyField.setPlaceholder("e.g., production.db.password");
        keyField.getStyle().set("flex-grow", "1");
        keyField.getElement().executeJs(
                "this.inputElement.setAttribute('data-bwignore', 'true');"
                + "this.inputElement.setAttribute('data-1p-ignore', 'true');"
                + "this.inputElement.setAttribute('autocomplete', 'new-password');"
        );

        PasswordField valueField = new PasswordField("Secret Plaintext Value");
        valueField.setPlaceholder("••••••••••••");
        valueField.getStyle().set("flex-grow", "1");
        valueField.getElement().executeJs(
                "this.inputElement.setAttribute('data-bwignore', 'true');"
                + "this.inputElement.setAttribute('data-1p-ignore', 'true');"
                + "this.inputElement.setAttribute('autocomplete', 'new-password');"
        );

        Button saveButton = new Button("Encrypt & Commit", e -> {
            if (!keyField.isEmpty() && !valueField.isEmpty()) {
                try {
                    secretService.encryptAndSave(keyField.getValue(), valueField.getValue());
                    Notification.show("Saved successfully!", 2000, Notification.Position.BOTTOM_END);
                    keyField.clear();
                    valueField.clear();
                    refreshGrid();
                } catch (Exception ex) {
                    Notification.show("Encryption Error!", 3000, Notification.Position.MIDDLE);
                }
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout formLayout = new HorizontalLayout(keyField, valueField, saveButton);
        formLayout.addClassName("glass-panel");
        formLayout.setWidthFull();
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.setAlignItems(Alignment.BASELINE);

        add(topBar, grid, formLayout);
    }

    private void refreshGrid() {
        grid.setItems(secretRepository.findAll());
        if (grid.getDataProvider() != null) {
            grid.getDataProvider().refreshAll();
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (adminRepository.count() == 0) {
            event.forwardTo(SetupView.class);
        } else if (!sessionManager.isUnlocked()) {
            event.forwardTo(UnlockView.class);
        } else {
            refreshGrid();
        }
    }
}
