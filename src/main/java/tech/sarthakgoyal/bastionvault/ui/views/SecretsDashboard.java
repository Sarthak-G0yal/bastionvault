package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
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

        // Grid Columns
        grid.addColumn(SecretDocument::getKey).setHeader("Secret Key").setAutoWidth(true);
        grid.addColumn(SecretDocument::getLastUpdated).setHeader("Last Updated");

        // Action Column: Delete Button
        grid.addComponentColumn(secret -> {
            Button deleteBtn = new Button("Delete", ev -> {
                secretRepository.deleteById(secret.getKey());
                refreshGrid();
                Notification.show("Deleted " + secret.getKey(), 2000, Notification.Position.BOTTOM_END);
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            return deleteBtn;
        }).setHeader("Actions");

        // Form fields with explicit Bitwarden bypass
        TextField keyField = new TextField("New Key");
        keyField.getElement().setAttribute("data-bitwarden-ignore", "");
        keyField.getElement().setAttribute("autocomplete", "off");

        PasswordField valueField = new PasswordField("Secret Value");
        valueField.getElement().setAttribute("data-bitwarden-ignore", "");
        valueField.getElement().setAttribute("autocomplete", "new-password");

        Button saveButton = new Button("Encrypt & Save", e -> {
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
        formLayout.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        Button lockButton = new Button("Lock Vault", e -> {
            sessionManager.lockVault();
            getUI().ifPresent(ui -> ui.navigate("unlock"));
        });
        lockButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        add(new HorizontalLayout(new H3("Active Secrets"), lockButton), grid, formLayout);
        setSizeFull();
    }

    private void refreshGrid() {
        grid.setItems(secretRepository.findAll());
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
