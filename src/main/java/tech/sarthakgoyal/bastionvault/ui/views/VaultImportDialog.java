package tech.sarthakgoyal.bastionvault.ui.views;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.SecretCodec;
import tech.sarthakgoyal.bastionvault.service.SecretFormat;
import tech.sarthakgoyal.bastionvault.service.SecretImportService;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;

/** Restore a whole-vault JSON/YAML file ({group: {KEY: value}}) with a per-group preview. */
class VaultImportDialog extends Dialog {

    private record Row(String group, int keys, int existing) {
    }

    private final SecretService secrets;
    private final SecretImportService importer;
    private final SecretCodec codec;
    private final AuditService audit;
    private final String admin;

    private final Checkbox overwrite = new Checkbox("Overwrite keys that already exist");
    private final Grid<Row> preview = new Grid<>(Row.class, false);
    private final Span summary = new Span();
    private final VerticalLayout warnings = new VerticalLayout();
    private final Button importButton = new Button("Import");

    private SecretCodec.ParsedVault parsed;
    private String fileName;

    VaultImportDialog(SecretService secrets, SecretImportService importer, SecretCodec codec, AuditService audit,
            String admin) {
        this.secrets = secrets;
        this.importer = importer;
        this.codec = codec;
        this.audit = audit;
        this.admin = admin;
        setHeaderTitle("Import vault file");
        setWidth("760px");

        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> onFile(metadata.fileName(), data)));
        upload.setMaxFiles(1);
        upload.setMaxFileSize(SecretCodec.MAX_VAULT_BYTES);
        upload.setAcceptedFileTypes(".json", ".yaml", ".yml", "application/json", "application/yaml");
        upload.setDropAllowed(true);
        upload.setWidthFull();
        upload.addFileRejectedListener(e -> UiSupport.error(e.getErrorMessage()));
        upload.addFileRemovedListener(e -> {
            parsed = null;
            refresh();
        });

        preview.addColumn(Row::group).setHeader("Group").setAutoWidth(true);
        preview.addColumn(Row::keys).setHeader("Keys");
        preview.addColumn(r -> r.keys() - r.existing()).setHeader("New");
        preview.addColumn(Row::existing).setHeader("Already exist");
        preview.addThemeVariants(GridVariant.LUMO_COMPACT);
        preview.setAllRowsVisible(true);
        preview.setVisible(false);
        warnings.setPadding(false);
        warnings.setSpacing(false);
        overwrite.addValueChangeListener(e -> refresh());

        importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importButton.setEnabled(false);
        importButton.addClickListener(e -> runImport());

        add(new VerticalLayout(new Paragraph("A vault file maps each group to its keys, as produced by "
                + "Export everything. Groups are created as needed. The file is read in memory only."),
                upload, overwrite, summary, preview, warnings));
        getFooter().add(new Button("Cancel", e -> close()), importButton);
        addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                parsed = null;
            }
        });
    }

    private void onFile(String name, byte[] data) {
        try {
            SecretFormat format = SecretFormat.fromFileName(name);
            if (format == SecretFormat.ENV) {
                format = SecretFormat.JSON;
            }
            parsed = codec.readVault(new String(data, StandardCharsets.UTF_8), format);
            fileName = name;
        } catch (IllegalArgumentException ex) {
            parsed = null;
            UiSupport.error(ex.getMessage());
        }
        refresh();
    }

    private void refresh() {
        warnings.removeAll();
        if (parsed == null) {
            preview.setVisible(false);
            summary.setText("");
            importButton.setEnabled(false);
            return;
        }
        List<Row> rows = parsed.groups().entrySet().stream().map(e -> {
            Set<String> existing = existingKeys(e.getKey());
            int overlap = (int) e.getValue().keySet().stream().filter(existing::contains).count();
            return new Row(e.getKey(), e.getValue().size(), overlap);
        }).toList();
        preview.setItems(rows);
        preview.setVisible(!rows.isEmpty());
        int total = parsed.keyCount();
        int existing = rows.stream().mapToInt(Row::existing).sum();
        int willStore = overwrite.getValue() ? total : total - existing;
        summary.setText(fileName + ": " + rows.size() + " groups, " + total + " keys, " + willStore + " will be stored");
        parsed.warnings().forEach(w -> {
            Span s = new Span("⚠ " + w);
            s.addClassName("muted");
            warnings.add(s);
        });
        importButton.setText("Import " + willStore + " secret" + (willStore == 1 ? "" : "s"));
        importButton.setEnabled(willStore > 0);
    }

    private Set<String> existingKeys(String group) {
        try {
            return secrets.existingKeys(group);
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private void runImport() {
        try {
            SecretImportService.VaultResult result = importer.importVault(parsed, overwrite.getValue(),
                    "admin:" + admin);
            audit.record(ActorType.ADMIN, admin, "IMPORT_ALL", null, Outcome.SUCCESS, "file=" + fileName
                    + " groups=" + result.groups().size() + " created=" + result.created() + " updated="
                    + result.updated() + " skipped=" + result.skippedExisting());
            String message = "Imported " + result.groups().size() + " groups: " + result.created() + " created, "
                    + result.updated() + " updated, " + result.skippedExisting() + " skipped";
            List<String> extra = result.warnings().subList(parsed.warnings().size(), result.warnings().size());
            if (extra.isEmpty()) {
                UiSupport.success(message);
            } else {
                UiSupport.error(message + ". Problems: " + String.join("; ", extra));
            }
            close();
        } catch (RuntimeException ex) {
            UiSupport.error(ex.getMessage());
        }
    }
}
