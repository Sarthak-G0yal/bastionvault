package tech.sarthakgoyal.bastionvault.ui.layout;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
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
 * Shell for every signed-in view: a sidebar grouped by section, with the seal state (the one dark
 * "vault door" card) and the signed-in user at the bottom. Sends the user to setup when the vault
 * is uninitialized and to the unseal page while sealed.
 */
@RolesAllowed("ADMIN")
public class MainLayout extends AppLayout implements BeforeEnterObserver, AfterNavigationObserver {

    private final KeyManager keys;
    private final UnsealService unseal;
    private final AuthenticationContext auth;
    private final Div sealCard = new Div();

    public MainLayout(KeyManager keys, UnsealService unseal, AuthenticationContext auth) {
        this.keys = keys;
        this.unseal = unseal;
        this.auth = auth;
        setPrimarySection(Section.DRAWER);

        // narrow screens only: the drawer collapses behind a toggle
        Div navbar = new Div(new DrawerToggle(), new Span("BastionVault"));
        navbar.addClassName("bv-navbar");
        addToNavbar(navbar);

        Div brandMark = new Div(UiSupport.icon("vault"));
        brandMark.addClassName("bv-brand-mark");
        Div brand = new Div(brandMark, new Span("BastionVault"));
        brand.addClassName("bv-brand");

        Div sidebar = new Div(brand,
                navLabel("Vault"),
                nav(new SideNavItem("Secrets", SecretsView.class, UiSupport.icon("key-round")),
                        new SideNavItem("Machine identities", ClientsView.class, UiSupport.icon("server"))),
                navLabel("Activity"),
                nav(new SideNavItem("Audit log", AuditView.class, UiSupport.icon("scroll-text"))),
                navLabel("System"),
                nav(new SideNavItem("Settings", SettingsView.class, UiSupport.icon("settings"))),
                spacer(), sealCard, userRow());
        sidebar.addClassName("bv-sidebar");
        addToDrawer(sidebar);
    }

    private static Span navLabel(String text) {
        Span label = new Span(text);
        label.addClassName("bv-nav-label");
        return label;
    }

    private static SideNav nav(SideNavItem... items) {
        SideNav nav = new SideNav();
        nav.addItem(items);
        return nav;
    }

    private static Div spacer() {
        Div spacer = new Div();
        spacer.addClassName("bv-sidebar-spacer");
        return spacer;
    }

    private Div userRow() {
        String name = UiSupport.admin(auth);
        Span avatar = new Span(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        avatar.addClassName("bv-avatar");
        Span label = new Span(name);
        label.addClassName("bv-user-name");
        Button logout = UiSupport.iconButton("log-out", "Log out", e -> auth.logout());
        Div row = new Div(avatar, label, UiSupport.themeToggle(), logout);
        row.addClassName("bv-user");
        return row;
    }

    private void renderSealCard() {
        sealCard.removeAll();
        sealCard.addClassName("bv-seal-card");
        boolean sealed = keys.isSealed();

        Span dot = new Span();
        dot.addClassName("bv-seal-dot");
        if (sealed) {
            dot.addClassName("sealed");
        }
        Div state = new Div(dot, new Span(sealed ? "Sealed" : "Unsealed"));
        state.addClassName("bv-seal-state");

        Div meta = new Div();
        meta.addClassName("bv-seal-meta");
        if (!sealed && keys.unsealedAt() != null) {
            meta.add(new Div("since " + UiSupport.DATE_TIME.format(keys.unsealedAt())));
        } else if (sealed) {
            meta.add(new Div("no secret can be read"));
        }
        meta.add(new Div("auto-unseal " + (keys.isInitialized() && keys.hasAutoUnseal() ? "on" : "off")));

        Button action = sealed
                ? new Button("Unseal", UiSupport.icon("lock-open"), e -> navigate(UnsealView.class))
                : new Button("Seal vault", UiSupport.icon("lock"), e -> {
                    unseal.seal(UiSupport.admin(auth));
                    navigate(UnsealView.class);
                });
        action.setWidthFull();
        sealCard.add(state, meta, action);
    }

    private void navigate(Class<? extends com.vaadin.flow.component.Component> view) {
        getUI().ifPresent(ui -> ui.navigate(view));
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
        renderSealCard();
    }
}
