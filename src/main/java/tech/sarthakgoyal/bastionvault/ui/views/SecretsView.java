package tech.sarthakgoyal.bastionvault.ui.views;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.domain.ApiClient;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.domain.Secret;
import tech.sarthakgoyal.bastionvault.service.ActivityService;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.SecretCodec;
import tech.sarthakgoyal.bastionvault.service.SecretFormat;
import tech.sarthakgoyal.bastionvault.service.SecretImportService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/**
 * Overview and secrets: live figures, the 14-day API reads chart, recent machine access, and one
 * "safe-deposit box" row per group listing its keys and which clients can open it. Values are only
 * decrypted on an explicit Reveal.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Secrets | BastionVault")
@RolesAllowed("ADMIN")
public class SecretsView extends Div {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final SecretService secrets;
    private final AuditService audit;
    private final AuthenticationContext auth;
    private final SecretCodec codec;
    private final ActivityService activity;
    private final ClientService clients;

    private final Div subtitle = new Div();
    private final Div stats = new Div();
    private final Div chartCard = new Div();
    private final Div recentCard = new Div();
    private final Div boxes = new Div();
    private final TextField search = new TextField();

    public SecretsView(SecretService secrets, AuditService audit, AuthenticationContext auth,
            SecretImportService importer, SecretCodec codec, ActivityService activity, ClientService clients) {
        this.secrets = secrets;
        this.audit = audit;
        this.auth = auth;
        this.codec = codec;
        this.activity = activity;
        this.clients = clients;
        setWidthFull();

        Div page = UiSupport.page();
        Div header = UiSupport.header("Secrets", null,
                UiSupport.secondary("Import file", "upload", e -> new ImportDialog(secrets, importer, codec, audit,
                        admin(), null, this::refresh).open()),
                UiSupport.primary("New secret", "plus", e -> openEditor(null, null)));
        subtitle.addClassName("bv-page-sub");
        header.getChildren().findFirst().ifPresent(left -> ((Div) left).add(subtitle));

        stats.addClassName("bv-stats");
        chartCard.addClassName("bv-card");
        recentCard.addClassName("bv-card");
        Div split = new Div(chartCard, recentCard);
        split.addClassName("bv-split");

        search.setPlaceholder("Search");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setPrefixComponent(UiSupport.icon("search"));
        search.addClassName("bv-search");
        search.addValueChangeListener(e -> renderBoxes());
        H2 groupsTitle = new H2("Groups");
        groupsTitle.addClassName("bv-section-title");
        Div groupsHead = new Div(groupsTitle, search);
        groupsHead.addClassName("bv-section-head");
        boxes.addClassName("bv-boxes");

        page.add(header, stats, split, groupsHead, boxes);
        add(page);
        refresh();
    }

    private String admin() {
        return UiSupport.admin(auth);
    }

    // ---------------------------------------------------------------- data

    private List<Secret> all = List.of();
    private ActivityService.Snapshot snapshot;
    private Map<String, List<String>> readers = Map.of();

    private void refresh() {
        all = secrets.listAll();
        snapshot = activity.snapshot(6);
        List<String> groupNames = all.stream().map(Secret::getGroupName).distinct().toList();
        readers = activity.readersByGroup(groupNames);
        subtitle.setText(all.size() + (all.size() == 1 ? " secret" : " secrets") + " in " + groupNames.size()
                + (groupNames.size() == 1 ? " group" : " groups"));
        renderStats(groupNames.size());
        renderChart();
        renderRecent();
        renderBoxes();
    }

    // ---------------------------------------------------------------- figures

    private void renderStats(int groupCount) {
        stats.removeAll();
        List<ApiClient> clientList = clients.list();
        long active = clientList.stream().filter(ApiClient::isActive).count();
        int delta = snapshot.reads24h() - snapshot.readsPrevious24h();
        stats.add(
                stat("Groups", String.valueOf(groupCount), null),
                stat("Secrets", String.valueOf(all.size()), null),
                stat("Machine clients", String.valueOf(active), "of " + clientList.size() + " registered"),
                stat("API reads, 24 h", String.valueOf(snapshot.reads24h()),
                        (delta >= 0 ? "+" : "") + delta + " vs previous 24 h"));
    }

    private static Div stat(String label, String value, String change) {
        Div l = new Div(label);
        l.addClassName("bv-stat-label");
        Div v = new Div(value);
        v.addClassName("bv-stat-value");
        Div card = UiSupport.card(l, v);
        if (change != null) {
            Div c = new Div(change);
            c.addClassName("bv-stat-change");
            card.add(c);
        }
        return card;
    }

    private void renderChart() {
        chartCard.removeAll();
        H2 title = new H2("API reads, last 14 days");
        title.addClassName("bv-section-title");
        Span total = new Span(snapshot.totalReads() + " reads");
        total.addClassName("bv-chart-total");
        Div head = new Div(title, total);
        head.addClassName("bv-chart-head");

        int max = snapshot.days().stream().mapToInt(ActivityService.Day::reads).max().orElse(0);
        Div chart = new Div();
        chart.addClassName("bv-chart");
        Div axis = new Div();
        axis.addClassName("bv-chart-axis");
        for (int i = 0; i < snapshot.days().size(); i++) {
            ActivityService.Day day = snapshot.days().get(i);
            Div bar = new Div();
            bar.addClassName("bv-bar");
            if (i == snapshot.days().size() - 1) {
                bar.addClassName("today");
            }
            bar.getStyle().set("height", max == 0 ? "0" : (day.reads() * 100.0 / max) + "%");
            bar.getElement().setAttribute("title",
                    DAY_LONG.format(day.date()) + " · " + day.reads() + (day.reads() == 1 ? " read" : " reads"));
            chart.add(bar);
            axis.add(new Span(DAY.format(day.date())));
        }
        if (max == 0) {
            Div empty = new Div("No machine has read a secret in the last 14 days");
            empty.addClassName("bv-chart-empty");
            chart.add(empty);
        }
        chartCard.add(head, chart, axis);
    }

    private void renderRecent() {
        recentCard.removeAll();
        H2 title = new H2("Recent machine access");
        title.addClassName("bv-section-title");
        Div list = new Div();
        list.addClassName("bv-recent");
        if (snapshot.recent().isEmpty()) {
            Div none = new Div("No machine access yet. Clients appear here after their first request.");
            none.addClassNames("bv-none");
            none.getStyle().set("margin-top", "12px");
            list.add(none);
        }
        for (AuditEvent e : snapshot.recent()) {
            Span client = new Span(e.getActor());
            client.addClassName("bv-recent-client");
            Span time = new Span(UiSupport.relative(e.getOccurredAt()));
            time.addClassName("bv-time");
            Div what = new Div();
            String target = e.getTarget();
            if (target != null && target.contains("/")) {
                what.add(UiSupport.address(target.substring(0, target.indexOf('/')),
                        target.substring(target.indexOf('/') + 1)));
            } else {
                Span t = new Span(target == null ? UiSupport.humanize(e.getAction()) : target);
                t.addClassName("bv-addr");
                what.add(t);
            }
            Div outcome = new Div();
            if (!Outcome.SUCCESS.name().equals(e.getOutcome())) {
                outcome.add(UiSupport.status(UiSupport.humanize(e.getOutcome()), "bad"));
            }
            Div row = new Div(client, time, what, outcome);
            row.addClassName("bv-recent-row");
            list.add(row);
        }
        recentCard.add(title, list);
    }

    // ---------------------------------------------------------------- group boxes

    private void renderBoxes() {
        boxes.removeAll();
        String q = search.getValue() == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        Map<String, List<Secret>> byGroup = new LinkedHashMap<>();
        for (Secret s : all) {
            byGroup.computeIfAbsent(s.getGroupName(), g -> new ArrayList<>()).add(s);
        }
        int shown = 0;
        for (Map.Entry<String, List<Secret>> entry : byGroup.entrySet()) {
            String group = entry.getKey();
            List<Secret> keys = entry.getValue();
            boolean groupMatch = q.isEmpty() || group.toLowerCase(Locale.ROOT).contains(q);
            List<Secret> matchingKeys = groupMatch ? keys
                    : keys.stream().filter(s -> s.getSecretKey().toLowerCase(Locale.ROOT).contains(q)).toList();
            if (matchingKeys.isEmpty()) {
                continue;
            }
            Details box = new Details(boxSummary(group, keys), boxContent(group, matchingKeys));
            box.addClassName("bv-box");
            box.setOpened(!q.isEmpty());
            boxes.add(box);
            shown++;
        }
        if (shown == 0) {
            Div none = new Div(all.isEmpty() ? "No secrets yet. Add one with New secret, or import a file."
                    : "Nothing matches “" + search.getValue() + "”");
            none.addClassNames("bv-card", "bv-none");
            boxes.add(none);
        }
    }

    private Div boxSummary(String group, List<Secret> keys) {
        Span name = new Span(group);
        name.addClassName("bv-box-name");

        List<String> openFor = readers.getOrDefault(group, List.of());
        Div chips = new Div();
        chips.addClassName("bv-chips");
        if (openFor.isEmpty()) {
            Span none = new Span("No client");
            none.addClassName("bv-none");
            chips.add(none);
        } else {
            openFor.forEach(c -> {
                Span chip = new Span(c);
                chip.addClassName("bv-chip");
                chips.add(chip);
            });
        }
        Instant changed = keys.stream().map(Secret::getUpdatedAt).max(Instant::compareTo).orElse(null);
        Instant read = snapshot.lastReadByGroup().get(group);

        Div summary = new Div(name,
                column("Keys", new Span(String.valueOf(keys.size())), false),
                column("Opens for", chips, false),
                column("Last change", new Span(changed == null ? "-" : UiSupport.DATE_TIME.format(changed)), true),
                column("Last read", new Span(read == null ? "None in 14 d" : UiSupport.relative(read)), true));
        summary.addClassName("bv-box-summary");
        return summary;
    }

    private static Div column(String label, com.vaadin.flow.component.Component value, boolean hideNarrow) {
        Span l = new Span(label);
        l.addClassName("bv-col-label");
        Div v = new Div(value);
        v.addClassName("bv-col-value");
        Div col = new Div(l, v);
        if (hideNarrow) {
            col.addClassName("bv-hide-narrow");
        }
        return col;
    }

    private Div boxContent(String group, List<Secret> keys) {
        Div content = new Div();

        Div head = new Div(new Span("Address"), new Span("Updated"), new Span("By"), exportLinks(group));
        head.addClassNames("bv-key-row", "head");
        content.add(head);

        for (Secret s : keys) {
            Span updated = new Span(UiSupport.DATE_TIME.format(s.getUpdatedAt()));
            updated.addClassName("bv-time");
            Div actions = new Div(
                    UiSupport.iconButton("eye", "Reveal", e -> reveal(s)),
                    UiSupport.iconButton("copy", "Copy address", e -> {
                        UiSupport.copy(s.getGroupName() + "/" + s.getSecretKey());
                        UiSupport.success("Copied " + s.getGroupName() + "/" + s.getSecretKey());
                    }),
                    UiSupport.iconButton("pencil", "Replace value", e -> openEditor(s.getGroupName(), s.getSecretKey())),
                    moreMenu(s));
            actions.addClassName("bv-row-actions");
            Div row = new Div(UiSupport.address(s.getGroupName(), s.getSecretKey()), updated,
                    UiSupport.actor(s.getUpdatedBy()), actions);
            row.addClassName("bv-key-row");
            content.add(row);
        }
        return content;
    }

    private Button moreMenu(Secret s) {
        Button more = UiSupport.iconButton("ellipsis", "More", e -> { });
        ContextMenu menu = new ContextMenu(more);
        menu.setOpenOnClick(true);
        menu.addItem("Delete", e -> confirmDelete(s)).addClassName("bv-danger-item");
        return more;
    }

    /** Download links for one group. The file is generated on click, audited, and never cached. */
    private Div exportLinks(String groupName) {
        String admin = admin();
        Div links = new Div();
        links.addClassName("bv-row-actions");
        for (SecretFormat format : SecretFormat.values()) {
            Anchor link = new Anchor(DownloadHandler.fromInputStream(event -> {
                byte[] body = codec.writeGroup(secrets.getGroup(groupName), format).getBytes(StandardCharsets.UTF_8);
                audit.admin(admin, "EXPORT_" + format.name(), groupName, Outcome.SUCCESS);
                return new DownloadResponse(new ByteArrayInputStream(body), groupName + format.extension(),
                        format.contentType(), body.length);
            }), format.label());
            link.getElement().setAttribute("download", true);
            link.getElement().setAttribute("title", "Export " + groupName + " as " + format.label());
            link.addClassName("export-link");
            links.add(link);
        }
        return links;
    }

    // ---------------------------------------------------------------- actions

    private void openEditor(String group, String key) {
        new SecretDialog(secrets, audit, admin(), group, key, this::refresh).open();
    }

    private void reveal(Secret s) {
        String target = s.getGroupName() + "/" + s.getSecretKey();
        try {
            String plain = secrets.get(s.getGroupName(), s.getSecretKey()).orElse("");
            audit.admin(admin(), "REVEAL_SECRET", target, Outcome.SUCCESS);
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(target);
            dialog.setWidth("560px");
            TextArea field = new TextArea();
            field.setValue(plain);
            field.setReadOnly(true);
            field.setWidthFull();
            field.addClassName("mono");
            dialog.add(field);
            dialog.getFooter().add(
                    UiSupport.secondary("Copy value", "copy", e -> {
                        UiSupport.copy(plain);
                        UiSupport.success("Copied");
                    }),
                    UiSupport.primary("Close", null, e -> dialog.close()));
            dialog.open();
        } catch (RuntimeException ex) {
            audit.admin(admin(), "REVEAL_SECRET", target, Outcome.FAILED);
            UiSupport.error("Could not decrypt: " + ex.getMessage());
        }
    }

    private void confirmDelete(Secret s) {
        String target = s.getGroupName() + "/" + s.getSecretKey();
        ConfirmDialog confirm = new ConfirmDialog("Delete " + target + "?",
                "Machines that read this secret get 404 from now on. This cannot be undone.", "Delete", e -> {
                    secrets.delete(s.getGroupName(), s.getSecretKey());
                    audit.admin(admin(), "DELETE_SECRET", target, Outcome.SUCCESS);
                    refresh();
                });
        confirm.setConfirmButtonTheme("error primary");
        confirm.setCancelable(true);
        confirm.open();
    }
}
