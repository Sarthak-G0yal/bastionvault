package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** The latest audit events. The same events go to the structured log for Loki. */
@Route(value = "audit", layout = MainLayout.class)
@PageTitle("Audit log | BastionVault")
@RolesAllowed("ADMIN")
public class AuditView extends VerticalLayout {

    private static final int LIMIT = 500;

    public AuditView(AuditService audit) {
        setSizeFull();
        Grid<AuditEvent> grid = new Grid<>(AuditEvent.class, false);
        grid.addColumn(e -> SecretsView.TIME.format(e.getOccurredAt())).setHeader("Time").setAutoWidth(true);
        grid.addColumn(AuditEvent::getActorType).setHeader("Type").setAutoWidth(true);
        grid.addColumn(AuditEvent::getActor).setHeader("Actor").setAutoWidth(true);
        grid.addColumn(AuditEvent::getAction).setHeader("Action").setAutoWidth(true);
        grid.addColumn(AuditEvent::getTarget).setHeader("Target").setAutoWidth(true);
        grid.addColumn(AuditEvent::getOutcome).setHeader("Outcome").setAutoWidth(true);
        grid.addColumn(AuditEvent::getSourceIp).setHeader("Source IP").setAutoWidth(true);
        grid.addColumn(AuditEvent::getDetail).setHeader("Detail").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.setItems(audit.latest(LIMIT));

        Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> grid.setItems(audit.latest(LIMIT)));
        add(new H2("Audit log"),
                new Paragraph("Latest " + LIMIT + " events. Older events are pruned after the retention period "
                        + "(VAULT_AUDIT_RETENTION_DAYS)."),
                refresh, grid);
        expand(grid);
    }
}
