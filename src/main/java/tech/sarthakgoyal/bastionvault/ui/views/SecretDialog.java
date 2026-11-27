package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;

/** Create a secret, or replace the value of an existing one (group and key prefilled). */
class SecretDialog extends Dialog {

    SecretDialog(SecretService secrets, AuditService audit, String admin, String group, String key,
            Runnable onSaved) {
        boolean editing = key != null;
        setHeaderTitle(editing ? "Replace value" : "New secret");
        setWidth("520px");

        TextField groupField = new TextField("Group");
        groupField.setPlaceholder("affine");
        groupField.setValue(group == null ? "" : group);
        TextField keyField = new TextField("Key");
        keyField.setPlaceholder("DATABASE_URL");
        keyField.setValue(key == null ? "" : key);
        groupField.setReadOnly(editing);
        keyField.setReadOnly(editing);
        TextArea value = new TextArea("Value");
        value.setHelperText("Multi-line values such as PEM keys are kept as they are");
        value.setMinHeight("96px");
        UiSupport.noAutofill(value);
        for (var f : new com.vaadin.flow.component.HasSize[] {groupField, keyField, value}) {
            f.setWidthFull();
        }

        Button generate = UiSupport.secondary("Generate value", "refresh-cw", e -> {
            try {
                var result = secrets.generateIfAbsent(groupField.getValue().trim(), keyField.getValue().trim(), 32,
                        "admin:" + admin);
                audit.admin(admin, "GENERATE_SECRET", groupField.getValue() + "/" + keyField.getValue(),
                        Outcome.SUCCESS);
                UiSupport.success(result.created() ? "Random 32-character value stored"
                        : "That secret already exists, so it was left unchanged");
                close();
                onSaved.run();
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });
        generate.setVisible(!editing);
        generate.setTooltipText("Store a random 32-character value if the secret does not exist yet");

        Button save = UiSupport.primary(editing ? "Save value" : "Save secret", null, e -> {
            try {
                boolean created = secrets.put(groupField.getValue().trim(), keyField.getValue().trim(),
                        value.getValue(), "admin:" + admin);
                audit.admin(admin, "WRITE_SECRET", groupField.getValue() + "/" + keyField.getValue(), Outcome.SUCCESS);
                UiSupport.success(created ? "Secret saved" : "Value replaced");
                close();
                onSaved.run();
            } catch (RuntimeException ex) {
                UiSupport.error(ex.getMessage());
            }
        });

        VerticalLayout body = new VerticalLayout(groupField, keyField, value);
        body.setPadding(false);
        add(body);
        getFooter().add(new Button("Cancel", e -> close()), generate, save);
        (editing ? value : groupField.isEmpty() ? groupField : keyField).focus();
    }
}
