package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import tech.sarthakgoyal.bastionvault.service.SetupService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;

/** First-run wizard: admin account + master password, protected by the setup token from the log. */
@Route("setup")
@PageTitle("Initialize | BastionVault")
@AnonymousAllowed
public class SetupView extends VerticalLayout implements BeforeEnterObserver {

    private final SetupService setup;

    public SetupView(SetupService setup) {
        this.setup = setup;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout card = new VerticalLayout();
        card.addClassName("glass-panel");
        card.setMaxWidth("520px");

        TextField token = new TextField("Setup token");
        token.setHelperText("Printed in the server log on first start (or set with VAULT_SETUP_TOKEN).");
        TextField username = new TextField("Admin username");
        PasswordField loginPassword = new PasswordField("Login password");
        PasswordField loginConfirm = new PasswordField("Repeat login password");
        PasswordField master = new PasswordField("Master password");
        master.setHelperText("Unseals the vault. Must differ from the login password. At least 12 characters.");
        PasswordField masterConfirm = new PasswordField("Repeat master password");
        for (var field : new com.vaadin.flow.component.HasElement[] {token, username, loginPassword, loginConfirm,
                master, masterConfirm}) {
            UiSupport.noAutofill(field);
            ((com.vaadin.flow.component.HasSize) field).setWidthFull();
        }

        Button init = new Button("Initialize vault", e -> {
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
                        "This key unseals the vault if you forget the master password. Store it offline "
                                + "(password manager outside the homelab, plus paper). Without the master password "
                                + "or this key, the data cannot be recovered.",
                        recoveryKey, () -> getUI().ifPresent(ui -> ui.navigate(LoginView.class)));
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        init.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        init.setWidthFull();

        card.add(new H2("Initialize BastionVault"),
                new Paragraph("Two separate secrets protect the vault: the login password lets you into this UI, "
                        + "the master password decrypts the data."),
                token, new H4("Administrator"), username, loginPassword, loginConfirm,
                new H4("Encryption"), master, masterConfirm, init);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (setup.isInitialized()) {
            event.forwardTo(LoginView.class);
        }
    }
}
