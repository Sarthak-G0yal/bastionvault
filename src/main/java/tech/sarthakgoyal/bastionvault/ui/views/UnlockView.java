package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
        setPadding(false);
        setSpacing(false);

        // Core Glassmorphic Card
        VerticalLayout glassCard = new VerticalLayout();
        glassCard.addClassName("glass-panel");
        glassCard.setMaxWidth("400px");
        glassCard.setPadding(true);
        glassCard.setSpacing(true);
        glassCard.setAlignItems(Alignment.CENTER);

        H2 title = new H2("Vault is Locked");
        title.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-xs)");

        Paragraph subtitle = new Paragraph("Provide the Master Key to safely decrypt your storage instance.");
        subtitle.getStyle()
                .set("text-align", "center")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "var(--lumo-space-m)");

        PasswordField masterPasswordField = new PasswordField("Master Password");
        masterPasswordField.setWidthFull();
        masterPasswordField.setPlaceholder("••••••••••••");
        masterPasswordField.getElement().executeJs(
                "this.inputElement.setAttribute('data-bwignore', 'true');"
                + "this.inputElement.setAttribute('data-1p-ignore', 'true');"
                + "this.inputElement.setAttribute('autocomplete', 'new-password');"
        );

        Button unlockButton = new Button("Unlock Instance", e -> {
            try {
                sessionManager.unlockVault(masterPasswordField.getValue());
                getUI().ifPresent(ui -> ui.navigate(""));
            } catch (Exception ex) {
                Notification.show("Invalid Master Password!", 3000, Notification.Position.MIDDLE);
            }
        });
        unlockButton.setWidthFull();
        unlockButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        unlockButton.addClickShortcut(Key.ENTER);
        unlockButton.getStyle().set("margin-top", "var(--lumo-space-m)");

        glassCard.add(title, subtitle, masterPasswordField, unlockButton);
        add(glassCard);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (adminRepository.count() == 0) {
            event.forwardTo(SetupView.class);
        }
    }
}
