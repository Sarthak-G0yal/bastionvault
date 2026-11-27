package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
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

/** The vault door: unseal with the master password or the recovery key. Requires an admin login. */
@Route(value = "unseal", layout = MainLayout.class)
@PageTitle("Unseal | BastionVault")
@RolesAllowed("ADMIN")
public class UnsealView extends Div implements BeforeEnterObserver {

    private static final String PASSWORD = "Master password";
    private static final String RECOVERY = "Recovery key";

    private final KeyManager keys;

    public UnsealView(KeyManager keys, UnsealService unseal, AuthenticationContext auth) {
        this.keys = keys;
        addClassName("bv-door-wrap");

        RadioButtonGroup<String> method = new RadioButtonGroup<>("Unseal with");
        method.setItems(PASSWORD, RECOVERY);
        method.setValue(PASSWORD);
        method.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);

        PasswordField secret = new PasswordField(PASSWORD);
        secret.setWidthFull();
        UiSupport.noAutofill(secret);
        method.addValueChangeListener(e -> secret.setLabel(e.getValue()));

        Button button = new Button("Unseal", UiSupport.icon("lock-open"), e -> {
            String admin = UiSupport.admin(auth);
            try {
                boolean ok = PASSWORD.equals(method.getValue())
                        ? unseal.withPassword(admin, secret.getValue())
                        : unseal.withRecoveryKey(admin, secret.getValue());
                secret.clear();
                if (ok) {
                    getUI().ifPresent(ui -> ui.navigate(SecretsView.class));
                } else {
                    UiSupport.error("That did not unseal the vault");
                }
            } catch (TooManyAttemptsException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        button.addClassName("bv-accent");
        button.addClickShortcut(Key.ENTER);
        button.setWidthFull();

        Div dial = new Div(UiSupport.icon("lock"));
        dial.addClassName("bv-door-dial");
        H1 title = new H1("Vault is sealed");
        Paragraph text = new Paragraph("The data key is not in memory, so no secret can be read or written, by you or "
                + "by any machine client.");
        Div door = new Div(dial, title, text, method, secret, button);
        door.addClassName("bv-door");
        add(door);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!keys.isSealed()) {
            event.forwardTo(SecretsView.class);
        }
    }
}
