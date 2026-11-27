package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** The latest audit events. The same events go to the structured log for Loki. */
@Route(value = "audit", layout = MainLayout.class)
@PageTitle("Audit log | BastionVault")
@RolesAllowed("ADMIN")
public class AuditView extends Div {

    private static final int LIMIT = 500;

    public AuditView(AuditService audit, VaultProperties properties) {
        setWidthFull();
        Grid<AuditEvent> grid = new Grid<>(AuditEvent.class, false);
        grid.addComponentColumn(e -> {
            Span t = new Span(UiSupport.DATE_TIME.format(e.getOccurredAt()));
            t.addClassName("bv-time");
            return t;
        }).setHeader("Time").setAutoWidth(true);
        grid.addComponentColumn(e -> {
            Span name = new Span(e.getActor());
            Span type = new Span(e.getActorType().toLowerCase());
            type.addClassName("bv-actor-type");
            return new Span(name, type);
        }).setHeader("Actor").setAutoWidth(true);
        grid.addColumn(e -> UiSupport.humanize(e.getAction())).setHeader("Action").setAutoWidth(true);
        grid.addComponentColumn(e -> {
            Span t = new Span(e.getTarget() == null ? "" : e.getTarget());
            t.addClassName("bv-addr");
            return t;
        }).setHeader("Target").setFlexGrow(1);
        grid.addComponentColumn(e -> UiSupport.status(UiSupport.humanize(e.getOutcome()), switch (e.getOutcome()) {
            case "SUCCESS" -> "ok";
            case "DENIED", "FAILED" -> "bad";
            default -> "";
        })).setHeader("Outcome").setAutoWidth(true);
        grid.addComponentColumn(e -> {
            Span ip = new Span(e.getSourceIp() == null ? "" : e.getSourceIp());
            ip.addClassName("bv-time");
            return ip;
        }).setHeader("Source IP").setAutoWidth(true);
        grid.addColumn(e -> e.getDetail() == null ? "" : e.getDetail()).setHeader("Detail").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setHeight("640px");
        grid.setItems(audit.latest(LIMIT));

        Div page = UiSupport.page();
        page.add(UiSupport.header("Audit log",
                        "Latest " + LIMIT + " events, kept for " + properties.audit().retentionDays() + " days",
                        UiSupport.secondary("Refresh", "refresh-cw", e -> grid.setItems(audit.latest(LIMIT)))),
                grid);
        add(page);
    }
}
