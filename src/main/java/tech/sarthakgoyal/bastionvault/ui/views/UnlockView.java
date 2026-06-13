package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.repository.AdminProfileRepository;

@Route("unlock")
@PageTitle("Unlock Vault | Bastion")
public class UnlockView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminProfileRepository adminRepository;

    public UnlockView(VaultSessionManager sessionManager, AdminProfileRepository adminRepository) {
        this.adminRepository = adminRepository;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H2 title = new H2("Vault is Locked");

        PasswordField masterPasswordField = new PasswordField("Master Password");
        masterPasswordField.setWidth("300px");
        // Block Bitwarden interference safely
        masterPasswordField.getElement().setAttribute("data-bitwarden-ignore", "");
        masterPasswordField.getElement().setAttribute("autocomplete", "current-password");

        Button unlockButton = new Button("Unlock", e -> {
            try {
                sessionManager.unlockVault(masterPasswordField.getValue());
                getUI().ifPresent(ui -> ui.navigate(""));
            } catch (Exception ex) {
                Notification.show("Invalid Master Password!", 3000, Notification.Position.MIDDLE);
            }
        });

        unlockButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        unlockButton.addClickShortcut(Key.ENTER);

        add(title, masterPasswordField, unlockButton);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (adminRepository.count() == 0) {
            event.forwardTo(SetupView.class);
        }
    }
}
