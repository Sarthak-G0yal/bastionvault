package tech.sarthakgoyal.bastionvault.ui.views;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.domain.Secret;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

/** Create, update, reveal and delete secrets. Values are only decrypted on explicit "Reveal". */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Secrets | BastionVault")
@RolesAllowed("ADMIN")
public class SecretsView extends VerticalLayout {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final SecretService secrets;
    private final AuditService audit;
    private final AuthenticationContext auth;

    private final TextField group = new TextField("Group");
    private final TextField key = new TextField("Key");
    private final TextArea value = new TextArea("Value");
    private final TextField filter = new TextField();
    private final Accordion groups = new Accordion();

    public SecretsView(SecretService secrets, AuditService audit, AuthenticationContext auth) {
        this.secrets = secrets;
        this.audit = audit;
        this.auth = auth;
        setSizeFull();

        group.setPlaceholder("e.g. affine");
        key.setPlaceholder("e.g. DATABASE_URL");
        value.setPlaceholder("Multi-line values (PEM keys) are fine");
        value.setMaxHeight("10em");
        UiSupport.noAutofill(value);

        Button save = new Button("Encrypt & save", VaadinIcon.LOCK.create(), e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button generate = new Button("Generate random (32)", VaadinIcon.MAGIC.create(), e -> generate());

        FormLayout form = new FormLayout(group, key, value);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("700px", 3));

        filter.setPlaceholder("Filter groups and keys");
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> refresh());
        filter.setPrefixComponent(VaadinIcon.SEARCH.create());

        groups.setWidthFull();
        add(new H2("Secrets"), form, new HorizontalLayout(save, generate), filter, groups);
        refresh();
    }

    private void save() {
        try {
            boolean created = secrets.put(group.getValue().trim(), key.getValue().trim(), value.getValue(),
                    UiSupport.actor(auth));
            audit.admin(UiSupport.admin(auth), "WRITE_SECRET", group.getValue() + "/" + key.getValue(), Outcome.SUCCESS);
            UiSupport.success(created ? "Secret created" : "Secret updated");
            key.clear();
            value.clear();
            refresh();
        } catch (RuntimeException ex) {
            UiSupport.error(ex.getMessage());
        }
    }

    private void generate() {
        try {
            var result = secrets.generateIfAbsent(group.getValue().trim(), key.getValue().trim(), 32,
                    UiSupport.actor(auth));
            audit.admin(UiSupport.admin(auth), "GENERATE_SECRET", group.getValue() + "/" + key.getValue(),
                    Outcome.SUCCESS);
            UiSupport.success(result.created() ? "Random value generated" : "Secret already exists; left unchanged");
            key.clear();
            refresh();
        } catch (RuntimeException ex) {
            UiSupport.error(ex.getMessage());
        }
    }

    private void refresh() {
        groups.getChildren().toList().forEach(groups::remove);
        String q = filter.getValue() == null ? "" : filter.getValue().toLowerCase(Locale.ROOT);
        Map<String, List<Secret>> byGroup = new LinkedHashMap<>();
        for (Secret s : secrets.listAll()) {
            if (q.isEmpty() || s.getGroupName().toLowerCase(Locale.ROOT).contains(q)
                    || s.getSecretKey().toLowerCase(Locale.ROOT).contains(q)) {
                byGroup.computeIfAbsent(s.getGroupName(), g -> new java.util.ArrayList<>()).add(s);
            }
        }
        if (byGroup.isEmpty()) {
            groups.add("No secrets yet", new Paragraph("Add one with the form above."));
            return;
        }
        byGroup.forEach((name, list) -> groups.add(name + " (" + list.size() + ")", grid(list)));
    }

    private Grid<Secret> grid(List<Secret> items) {
        Grid<Secret> grid = new Grid<>(Secret.class, false);
        grid.addColumn(Secret::getSecretKey).setHeader("Key").setAutoWidth(true);
        grid.addColumn(s -> "••••••••").setHeader("Value");
        grid.addColumn(s -> TIME.format(s.getUpdatedAt())).setHeader("Updated").setAutoWidth(true);
        grid.addColumn(Secret::getUpdatedBy).setHeader("By").setAutoWidth(true);
        grid.addComponentColumn(s -> {
            Button reveal = new Button("Reveal", VaadinIcon.EYE.create(), e -> reveal(s));
            Button edit = new Button("Edit", VaadinIcon.EDIT.create(), e -> {
                group.setValue(s.getGroupName());
                key.setValue(s.getSecretKey());
                value.clear();
                value.focus();
            });
            Button delete = new Button("Delete", VaadinIcon.TRASH.create(), e -> confirmDelete(s));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            for (Button b : List.of(reveal, edit, delete)) {
                b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            }
            return new HorizontalLayout(reveal, edit, delete);
        }).setHeader("Actions").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(true);
        grid.setItems(items);
        return grid;
    }

    private void reveal(Secret s) {
        String target = s.getGroupName() + "/" + s.getSecretKey();
        try {
            String plain = secrets.get(s.getGroupName(), s.getSecretKey()).orElse("");
            audit.admin(UiSupport.admin(auth), "REVEAL_SECRET", target, Outcome.SUCCESS);
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(target);
            dialog.setWidth("640px");
            TextArea field = new TextArea();
            field.setValue(plain);
            field.setReadOnly(true);
            field.setWidthFull();
            field.addClassName("mono");
            Button copy = new Button("Copy", e -> {
                getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", plain));
                UiSupport.success("Copied");
            });
            Button close = new Button("Close", e -> dialog.close());
            dialog.add(field);
            dialog.getFooter().add(copy, close);
            dialog.open();
        } catch (RuntimeException ex) {
            audit.admin(UiSupport.admin(auth), "REVEAL_SECRET", target, Outcome.FAILED);
            UiSupport.error("Could not decrypt: " + ex.getMessage());
        }
    }

    private void confirmDelete(Secret s) {
        String target = s.getGroupName() + "/" + s.getSecretKey();
        ConfirmDialog confirm = new ConfirmDialog("Delete " + target + "?",
                "Machines that read this secret will get 404 from now on.", "Delete", e -> {
                    secrets.delete(s.getGroupName(), s.getSecretKey());
                    audit.admin(UiSupport.admin(auth), "DELETE_SECRET", target, Outcome.SUCCESS);
                    refresh();
                });
        confirm.setConfirmButtonTheme("error primary");
        confirm.setCancelable(true);
        confirm.open();
    }
}
