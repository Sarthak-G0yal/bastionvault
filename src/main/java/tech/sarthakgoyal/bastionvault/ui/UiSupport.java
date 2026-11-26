package tech.sarthakgoyal.bastionvault.ui;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.security.AuthenticationContext;

/** Small shared helpers for the views. */
public final class UiSupport {

    private UiSupport() {
    }

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

    /**
     * Shows a value that is not stored anywhere in readable form (client secret, API key, recovery
     * key). The dialog can only be closed after confirming it was saved.
     */
    public static void showOnce(String title, String explanation, String value, Runnable onClose) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("640px");

        TextArea field = new TextArea();
        field.setValue(value);
        field.setReadOnly(true);
        field.setWidthFull();
        field.addClassName("mono");

        Button copy = new Button("Copy", e -> {
            UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", value);
            success("Copied to clipboard");
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

        VerticalLayout body = new VerticalLayout(new Paragraph(explanation), field, copy, confirm);
        body.setPadding(false);
        dialog.add(body);
        dialog.getFooter().add(done);
        dialog.open();
    }
}
