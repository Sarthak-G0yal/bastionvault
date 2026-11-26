package tech.sarthakgoyal.bastionvault.ui.layout;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.service.UnsealService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.views.AuditView;
import tech.sarthakgoyal.bastionvault.ui.views.ClientsView;
import tech.sarthakgoyal.bastionvault.ui.views.SecretsView;
import tech.sarthakgoyal.bastionvault.ui.views.SettingsView;
import tech.sarthakgoyal.bastionvault.ui.views.SetupView;
import tech.sarthakgoyal.bastionvault.ui.views.UnsealView;

/**
 * Shell for every logged-in view. Sends the user to setup when the vault is uninitialized and to
 * the unseal page while sealed, so no view ever runs without the data key.
 */
@RolesAllowed("ADMIN")
public class MainLayout extends AppLayout implements BeforeEnterObserver, AfterNavigationObserver {

    private final KeyManager keys;
    private final Span status = new Span();

    public MainLayout(KeyManager keys, UnsealService unseal, AuthenticationContext auth) {
        this.keys = keys;

        H1 logo = new H1("BastionVault");
        logo.addClassName("app-logo");

        Button sealButton = new Button("Seal vault", VaadinIcon.LOCK.create(), e -> {
            unseal.seal(UiSupport.admin(auth));
            getUI().ifPresent(ui -> ui.navigate(UnsealView.class));
        });
        sealButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        Button logout = new Button("Log out", VaadinIcon.SIGN_OUT.create(), e -> auth.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Span user = new Span(UiSupport.admin(auth));
        user.addClassName("muted");

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), logo, status, user, sealButton, logout);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(logo);
        header.setWidthFull();
        header.addClassName("app-header");
        addToNavbar(header);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Secrets", SecretsView.class, VaadinIcon.KEY.create()));
        nav.addItem(new SideNavItem("Machine identities", ClientsView.class, VaadinIcon.SERVER.create()));
        nav.addItem(new SideNavItem("Audit log", AuditView.class, VaadinIcon.LIST.create()));
        nav.addItem(new SideNavItem("Settings", SettingsView.class, VaadinIcon.COG.create()));
        addToDrawer(nav);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!keys.isInitialized()) {
            event.forwardTo(SetupView.class);
        } else if (keys.isSealed() && event.getNavigationTarget() != UnsealView.class) {
            event.forwardTo(UnsealView.class);
        }
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        boolean sealed = keys.isSealed();
        status.setText(sealed ? "Sealed" : "Unsealed");
        status.getElement().getThemeList().clear();
        status.getElement().getThemeList().add(sealed ? "badge error" : "badge success");
    }
}
