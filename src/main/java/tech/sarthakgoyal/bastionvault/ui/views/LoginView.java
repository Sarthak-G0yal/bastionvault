package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

import tech.sarthakgoyal.bastionvault.service.SetupService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;

/** Per-browser admin login (decision 1A). Posts to Spring Security's /login. */
@Route("login")
@PageTitle("Sign in | BastionVault")
@AnonymousAllowed
public class LoginView extends Div implements BeforeEnterObserver {

    private final SetupService setup;
    private final AuthenticationContext auth;
    private final LoginForm login = new LoginForm();

    public LoginView(SetupService setup, AuthenticationContext auth) {
        this.setup = setup;
        this.auth = auth;
        addClassName("bv-auth");

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setSubmit("Sign in");
        i18n.getErrorMessage().setTitle("Sign-in failed");
        i18n.getErrorMessage().setMessage(
                "Wrong username or password, or too many attempts. After 5 failures sign-in is paused for 15 minutes.");
        login.setI18n(i18n);
        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);

        H1 title = new H1("Sign in");
        title.addClassName("bv-auth-title");
        Paragraph text = new Paragraph("Signing in opens the console. Reading secrets also needs the vault to be "
                + "unsealed.");
        text.addClassName("bv-auth-text");
        Div card = new Div(UiSupport.brand(), title, text, login);
        card.addClassName("bv-auth-card");
        add(toggle(), card);
    }

    /** Theme toggle in the top-right corner of the signed-out screens. */
    static com.vaadin.flow.component.button.Button toggle() {
        var toggle = UiSupport.themeToggle();
        toggle.addClassName("bv-auth-toggle");
        return toggle;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!setup.isInitialized()) {
            event.forwardTo(SetupView.class);
            return;
        }
        if (auth.isAuthenticated()) {
            event.forwardTo(SecretsView.class);
            return;
        }
        login.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}
