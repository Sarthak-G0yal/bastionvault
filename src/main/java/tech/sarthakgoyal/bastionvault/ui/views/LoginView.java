package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

import tech.sarthakgoyal.bastionvault.service.SetupService;

/** Per-browser admin login (decision 1A). Posts to Spring Security's /login. */
@Route("login")
@PageTitle("Sign in | BastionVault")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final SetupService setup;
    private final AuthenticationContext auth;
    private final LoginForm login = new LoginForm();

    public LoginView(SetupService setup, AuthenticationContext auth) {
        this.setup = setup;
        this.auth = auth;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("BastionVault");
        i18n.getErrorMessage().setTitle("Sign-in failed");
        i18n.getErrorMessage().setMessage(
                "Wrong username or password, or too many attempts. After 5 failures, sign-in is blocked for 15 minutes.");
        login.setI18n(i18n);
        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);
        login.addClassName("glass-panel");
        add(login);
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
