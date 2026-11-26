package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.service.TooManyAttemptsException;
import tech.sarthakgoyal.bastionvault.service.UnsealService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** Unseal with the master password or the recovery key. Requires an admin login first. */
@Route(value = "unseal", layout = MainLayout.class)
@PageTitle("Unseal | BastionVault")
@RolesAllowed("ADMIN")
public class UnsealView extends VerticalLayout implements BeforeEnterObserver {

    private static final String PASSWORD = "Master password";
    private static final String RECOVERY = "Recovery key";

    private final KeyManager keys;

    public UnsealView(KeyManager keys, UnsealService unseal, AuthenticationContext auth) {
        this.keys = keys;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout card = new VerticalLayout();
        card.addClassName("glass-panel");
        card.setMaxWidth("440px");

        RadioButtonGroup<String> method = new RadioButtonGroup<>("Unseal with");
        method.setItems(PASSWORD, RECOVERY);
        method.setValue(PASSWORD);

        PasswordField secret = new PasswordField(PASSWORD);
        secret.setWidthFull();
        UiSupport.noAutofill(secret);
        method.addValueChangeListener(e -> secret.setLabel(e.getValue()));

        Button button = new Button("Unseal", e -> {
            String admin = UiSupport.admin(auth);
            try {
                boolean ok = PASSWORD.equals(method.getValue())
                        ? unseal.withPassword(admin, secret.getValue())
                        : unseal.withRecoveryKey(admin, secret.getValue());
                secret.clear();
                if (ok) {
                    getUI().ifPresent(ui -> ui.navigate(SecretsView.class));
                } else {
                    UiSupport.error("That did not unseal the vault.");
                }
            } catch (TooManyAttemptsException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClickShortcut(Key.ENTER);
        button.setWidthFull();

        card.add(new H2("Vault is sealed"),
                new Paragraph("The data key is not in memory, so no secret can be read or written, "
                        + "by you or by any machine client."),
                method, secret, button);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!keys.isSealed()) {
            event.forwardTo(SecretsView.class);
        }
    }
}
