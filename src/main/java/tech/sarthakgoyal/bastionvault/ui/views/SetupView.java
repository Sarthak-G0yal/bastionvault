package tech.sarthakgoyal.bastionvault.ui.views;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import tech.sarthakgoyal.bastionvault.model.AdminProfile;
import tech.sarthakgoyal.bastionvault.repository.AdminProfileRepository;

@Route("setup")
@PageTitle("Initialize Vault | Bastion")
public class SetupView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminProfileRepository adminRepository;

    public SetupView(AdminProfileRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H2 title = new H2("Welcome to Bastion Vault");
        Paragraph subtitle = new Paragraph("This is your first boot. Please configure your master credentials.");

        EmailField emailField = new EmailField("Administrator Email");
        emailField.setWidth("300px");
        emailField.getElement().executeJs(
                "this.inputElement.setAttribute('data-bwignore', 'true');"
                + "this.inputElement.setAttribute('data-1p-ignore', 'true');"
                + "this.inputElement.setAttribute('autocomplete', 'new-password');"
        );

        PasswordField passwordField = new PasswordField("Master Password");
        passwordField.setWidth("300px");
        passwordField.getElement().executeJs(
                "this.inputElement.setAttribute('data-bwignore', 'true');"
                + "this.inputElement.setAttribute('data-1p-ignore', 'true');"
                + "this.inputElement.setAttribute('autocomplete', 'new-password');"
        );

        Button saveButton = new Button("Initialize Vault", e -> {
            if (!emailField.isEmpty() && !passwordField.isEmpty()) {
                // Hash the password for login verification, save the profile
                String hash = passwordEncoder.encode(passwordField.getValue());
                AdminProfile profile = new AdminProfile(emailField.getValue(), hash);
                adminRepository.save(profile);

                Notification.show("Vault Initialized! Please login.", 3000, Notification.Position.MIDDLE);
                getUI().ifPresent(ui -> ui.navigate("unlock"));
            } else {
                Notification.show("Please fill out all fields.", 3000, Notification.Position.MIDDLE);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        saveButton.addClickShortcut(Key.ENTER);

        add(title, subtitle, emailField, passwordField, saveButton);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // If the admin profile already exists, kick them out of the setup screen
        if (adminRepository.count() > 0) {
            event.forwardTo(UnlockView.class);
        }
    }
}
