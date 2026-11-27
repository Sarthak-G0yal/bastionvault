package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import tech.sarthakgoyal.bastionvault.service.SetupService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;

/** First-run setup: admin account + master password, protected by the setup token from the log. */
@Route("setup")
@PageTitle("Set up | BastionVault")
@AnonymousAllowed
public class SetupView extends Div implements BeforeEnterObserver {

    private final SetupService setup;

    public SetupView(SetupService setup) {
        this.setup = setup;
        addClassName("bv-auth");

        TextField token = new TextField("Setup token");
        token.setHelperText("Printed in the server log on first start, or set with VAULT_SETUP_TOKEN");
        TextField username = new TextField("Admin username");
        PasswordField loginPassword = new PasswordField("Login password");
        PasswordField loginConfirm = new PasswordField("Repeat login password");
        PasswordField master = new PasswordField("Master password");
        master.setHelperText("Unseals the vault. At least 12 characters, different from the login password");
        PasswordField masterConfirm = new PasswordField("Repeat master password");
        for (HasElement field : new HasElement[] {token, username, loginPassword, loginConfirm, master, masterConfirm}) {
            UiSupport.noAutofill(field);
            ((HasSize) field).setWidthFull();
        }

        Button init = UiSupport.primary("Initialize vault", null, e -> {
            if (!loginPassword.getValue().equals(loginConfirm.getValue())) {
                UiSupport.error("The login passwords do not match");
                return;
            }
            if (!master.getValue().equals(masterConfirm.getValue())) {
                UiSupport.error("The master passwords do not match");
                return;
            }
            try {
                String recoveryKey = setup.initialize(token.getValue(), username.getValue(),
                        loginPassword.getValue(), master.getValue());
                UiSupport.showOnce("Recovery key",
                        "This key unseals the vault if you forget the master password. Store it offline, in a "
                                + "password manager outside the homelab and on paper. Without the master password or "
                                + "this key the data cannot be recovered.",
                        recoveryKey, () -> getUI().ifPresent(ui -> ui.navigate(LoginView.class)));
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        init.setWidthFull();

        H1 title = new H1("Set up this vault");
        title.addClassName("bv-auth-title");
        Paragraph text = new Paragraph("Two separate secrets protect it: the login password opens this console, the "
                + "master password decrypts the data.");
        text.addClassName("bv-auth-text");
        Div admin = new Div("Administrator");
        admin.addClassName("bv-form-section");
        Div encryption = new Div("Encryption");
        encryption.addClassName("bv-form-section");

        Div card = new Div(UiSupport.brand(), title, text, token, admin, username, loginPassword, loginConfirm,
                encryption, master, masterConfirm, init);
        card.addClassNames("bv-auth-card", "wide");
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (setup.isInitialized()) {
            event.forwardTo(LoginView.class);
        }
    }
}
