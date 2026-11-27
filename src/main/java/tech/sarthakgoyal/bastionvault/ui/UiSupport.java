package tech.sarthakgoyal.bastionvault.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.security.AuthenticationContext;

/** Shared building blocks for the views, so every screen uses the same pieces. */
public final class UiSupport {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    private UiSupport() {
    }

    // ---------------------------------------------------------------- icons and buttons

    /** A Lucide line icon from META-INF/resources/icons, 16px, colored by its text role. */
    public static SvgIcon icon(String name) {
        SvgIcon icon = new SvgIcon("icons/" + name + ".svg");
        icon.addClassName("bv-icon");
        return icon;
    }

    public static Button primary(String text, String iconName, ComponentEventListener<ClickEvent<Button>> click) {
        Button b = new Button(text, iconName == null ? null : icon(iconName), click);
        b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return b;
    }

    public static Button secondary(String text, String iconName, ComponentEventListener<ClickEvent<Button>> click) {
        Button b = new Button(text, iconName == null ? null : icon(iconName), click);
        b.addClassName("bv-secondary");
        return b;
    }

    /** Square icon-only button; the label becomes its tooltip and accessible name. */
    public static Button iconButton(String iconName, String label, ComponentEventListener<ClickEvent<Button>> click) {
        Button b = new Button(icon(iconName), click);
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        b.addClassName("bv-icon-btn");
        b.setAriaLabel(label);
        b.setTooltipText(label);
        return b;
    }

    // ---------------------------------------------------------------- page structure

    public static Div page() {
        Div page = new Div();
        page.addClassName("bv-page");
        return page;
    }

    /** Page title, a short line under it, and the page's actions on the right. */
    public static Div header(String title, String subtitle, com.vaadin.flow.component.Component... actions) {
        H1 h1 = new H1(title);
        h1.addClassName("bv-page-title");
        Div left = new Div(h1);
        if (subtitle != null) {
            Div sub = new Div(subtitle);
            sub.addClassName("bv-page-sub");
            left.add(sub);
        }
        Div right = new Div(actions);
        right.addClassName("bv-actions");
        Div header = new Div(left, right);
        header.addClassName("bv-page-header");
        return header;
    }

    public static Div card(com.vaadin.flow.component.Component... children) {
        Div card = new Div(children);
        card.addClassName("bv-card");
        return card;
    }

    /** Wordmark for the signed-out screens: a flat vault glyph and the product name. */
    public static Div brand() {
        Div mark = new Div(icon("vault"));
        mark.addClassName("bv-brand-mark");
        Div brand = new Div(mark, new Span("BastionVault"));
        brand.addClassName("bv-auth-brand");
        return brand;
    }

    /** "group/KEY" in mono, the group part muted: the address machines use to fetch it. */
    public static Span address(String group, String key) {
        Span grp = new Span(group + "/");
        grp.addClassName("grp");
        Span addr = new Span(grp, new Span(key));
        addr.addClassName("bv-addr");
        return addr;
    }

    public static Span status(String text, String kind) {
        Span s = new Span(text);
        s.addClassName("bv-status");
        if (kind != null && !kind.isBlank()) {
            s.addClassName(kind);
        }
        return s;
    }

    // ---------------------------------------------------------------- text

    /** "admin:sarthak" -> sarthak (admin), "client:ansible" -> ansible (client). */
    public static Span actor(String raw) {
        if (raw == null) {
            return new Span("-");
        }
        int colon = raw.indexOf(':');
        if (colon < 0) {
            return new Span(raw);
        }
        Span type = new Span(raw.substring(0, colon));
        type.addClassName("bv-actor-type");
        return new Span(new Span(raw.substring(colon + 1)), type);
    }

    public static String relative(Instant at) {
        if (at == null) {
            return "Never";
        }
        Duration age = Duration.between(at, Instant.now());
        if (age.toMinutes() < 1) {
            return "Just now";
        }
        if (age.toMinutes() < 60) {
            return age.toMinutes() + " min ago";
        }
        if (age.toHours() < 24) {
            return age.toHours() + " h ago";
        }
        if (age.toDays() < 14) {
            return age.toDays() + " d ago";
        }
        return DATE_TIME.format(at);
    }

    /** READ_SECRET -> Read secret */
    public static String humanize(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String text = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ---------------------------------------------------------------- fields and feedback

    /** Keeps browser password managers from auto-filling or saving vault fields. */
    public static void noAutofill(HasElement field) {
        field.getElement().executeJs(
                "const i = this.inputElement || this;"
                        + "i.setAttribute('data-bwignore', 'true');"
                        + "i.setAttribute('data-1p-ignore', 'true');"
                        + "i.setAttribute('data-lpignore', 'true');"
                        + "i.setAttribute('autocomplete', 'new-password');");
    }

    public static void success(String message) {
        Notification.show(message, 3000, Notification.Position.BOTTOM_END)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        Notification.show(message, 6000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    public static String admin(AuthenticationContext auth) {
        return auth.getPrincipalName().orElse("unknown");
    }

    public static String actor(AuthenticationContext auth) {
        return "admin:" + admin(auth);
    }

    public static void copy(String text) {
        UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", text);
    }

    /**
     * Shows a value that is not stored anywhere in readable form (client secret, API key, recovery
     * key). The dialog can only be closed after confirming it was saved.
     */
    public static void showOnce(String title, String explanation, String value, Runnable onClose) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("560px");

        TextArea field = new TextArea();
        field.setValue(value);
        field.setReadOnly(true);
        field.setWidthFull();
        field.addClassName("mono");

        Button copy = secondary("Copy", "copy", e -> {
            copy(value);
            success("Copied");
        });
        Checkbox confirm = new Checkbox("I have stored this somewhere safe. It cannot be shown again.");
        Button done = new Button("Done", e -> {
            dialog.close();
            if (onClose != null) {
                onClose.run();
            }
        });
        done.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        done.setEnabled(false);
        confirm.addValueChangeListener(e -> done.setEnabled(e.getValue()));

        Paragraph text = new Paragraph(explanation);
        text.addClassName("muted");
        VerticalLayout body = new VerticalLayout(text, field, copy, confirm);
        body.setPadding(false);
        dialog.add(body);
        dialog.getFooter().add(done);
        dialog.open();
    }
}
