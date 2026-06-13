package tech.sarthakgoyal.bastionvault.ui.views;

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

        // Inside the constructor of UnlockView.java
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        // CREATE THE FLOATING GLASS CARD
        VerticalLayout glassCard = new VerticalLayout();
        glassCard.addClassName("glass-panel");
        glassCard.setMaxWidth("400px");
        glassCard.setAlignItems(Alignment.CENTER);
        glassCard.setPadding(true);

        H2 title = new H2("Vault is Locked");

        PasswordField masterPasswordField = new PasswordField("Master Password");
        masterPasswordField.setWidthFull(); // Stretch to fill the card
        masterPasswordField.getElement().executeJs("this.inputElement.setAttribute('data-bitwarden-ignore', '');");
        masterPasswordField.getElement().executeJs("this.inputElement.setAttribute('data-1p-ignore', '');");
        Button unlockButton = new Button("Unlock", e -> {
            try {
                sessionManager.unlockVault(masterPasswordField.getValue());
                getUI().ifPresent(ui -> ui.navigate(""));
            } catch (Exception ex) {
                Notification.show("Invalid Master Password!", 3000, Notification.Position.MIDDLE);
            }
        });

        unlockButton.setWidthFull();
        unlockButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Add elements to the card, then add the card to the screen
        glassCard.add(title, masterPasswordField, unlockButton);
        add(glassCard);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (adminRepository.count() == 0) {
            event.forwardTo(SetupView.class);
        }
    }
}
