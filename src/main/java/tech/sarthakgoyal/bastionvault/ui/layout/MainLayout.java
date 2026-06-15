package tech.sarthakgoyal.bastionvault.ui.layout;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.ui.views.MachineIdentitiesView;
import tech.sarthakgoyal.bastionvault.ui.views.SecretsDashboardView;

public class MainLayout extends AppLayout {

    private final VaultSessionManager sessionManager;

    public MainLayout(VaultSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("BastionVault");
        logo.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        Button lockButton = new Button("Lock Vault", VaadinIcon.LOCK.create(), e -> {
            sessionManager.lockVault();
            UI.getCurrent().navigate("unlock");
        });
        lockButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), logo, lockButton);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(logo);
        header.setWidthFull();
        header.addClassNames("py-0", "px-m");

        addToNavbar(header);
    }

    private void createDrawer() {
        RouterLink secretsLink = new RouterLink("Secrets Groups", SecretsDashboardView.class);
        RouterLink clientsLink = new RouterLink("Machine Identities", MachineIdentitiesView.class);

        VerticalLayout layout = new VerticalLayout(secretsLink, clientsLink);
        layout.setSpacing(true);
        addToDrawer(layout);
    }
}