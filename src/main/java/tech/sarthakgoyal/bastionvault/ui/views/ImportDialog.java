package tech.sarthakgoyal.bastionvault.ui.views;

import java.util.List;
import java.util.Map;
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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.SecretCodec;
import tech.sarthakgoyal.bastionvault.service.SecretFormat;
import tech.sarthakgoyal.bastionvault.service.SecretImportService;
import tech.sarthakgoyal.bastionvault.service.Names;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.ui.UiSupport;

/**
 * Upload a .env, JSON or YAML file into one group. The file is parsed in memory, shown as a preview
 * (keys only, values masked), and nothing is stored until "Import" is clicked.
 */
class ImportDialog extends Dialog {

    private record Row(String key, int length, boolean exists) {
    }

    private final SecretService secrets;
    private final SecretImportService importer;
    private final SecretCodec codec;
    private final Select<SecretFormat> format = new Select<>();
    private final AuditService audit;
    private final String admin;
    private final Runnable onImported;

    private final TextField group = new TextField("Group");
    private final Checkbox overwrite = new Checkbox("Overwrite keys that already exist in the group");
    private final Grid<Row> preview = new Grid<>(Row.class, false);
    private final Span summary = new Span();
    private final VerticalLayout warnings = new VerticalLayout();
    private final Button importButton = new Button("Import");

    private SecretCodec.Parsed parsed;
    private String fileName;
    private byte[] fileData;

    ImportDialog(SecretService secrets, SecretImportService importer, SecretCodec codec, AuditService audit,
            String admin, String initialGroup, Runnable onImported) {
        this.secrets = secrets;
        this.importer = importer;
        this.codec = codec;
        this.audit = audit;
        this.admin = admin;
        this.onImported = onImported;

        setHeaderTitle("Import file");
        setWidth("760px");

        group.setValue(initialGroup == null ? "" : initialGroup);
        group.setPlaceholder("e.g. affine");
        group.setWidthFull();
        group.addValueChangeListener(e -> refreshPreview());
        overwrite.addValueChangeListener(e -> refreshPreview());

        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> onFile(metadata.fileName(), data)));
        upload.setMaxFiles(1);
        upload.setMaxFileSize(SecretCodec.MAX_GROUP_BYTES);
        upload.setAcceptedFileTypes(".env", ".json", ".yaml", ".yml", "text/plain", "application/json",
                "application/yaml", "application/octet-stream");

        format.setLabel("Format");
        format.setItems(SecretFormat.values());
        format.setItemLabelGenerator(SecretFormat::label);
        format.setHelperText("Detected from the file name");
        format.setWidth("240px");
        format.setValue(SecretFormat.ENV);
        format.addValueChangeListener(e -> {
            if (e.isFromClient() && fileData != null) {
                parse();
            }
        });
        upload.setDropAllowed(true);
        upload.setWidthFull();
        upload.addFileRejectedListener(e -> UiSupport.error(e.getErrorMessage()));
        upload.addFileRemovedListener(e -> clear());

        preview.addColumn(Row::key).setHeader("Key").setAutoWidth(true);
        preview.addColumn(r -> "•".repeat(Math.min(r.length(), 12)) + " (" + r.length() + " chars)").setHeader("Value");
        preview.addColumn(this::status).setHeader("Action").setAutoWidth(true);
        preview.addThemeVariants(GridVariant.LUMO_COMPACT);
        preview.setAllRowsVisible(true);
        preview.setVisible(false);
        warnings.setPadding(false);
        warnings.setSpacing(false);

        importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importButton.setEnabled(false);
        importButton.addClickListener(e -> runImport());
        Button cancel = new Button("Cancel", e -> close());

        add(new VerticalLayout(
                new Paragraph("Each key in the file becomes a secret in the group: KEY=value lines (.env), "
                        + "a flat JSON object or a flat YAML mapping. The file is read in memory only and is "
                        + "not stored."),
                group, upload, format, overwrite, summary, preview, warnings));
        getFooter().add(cancel, importButton);
        // Drop the parsed values as soon as the dialog closes, whatever the reason.
        addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                parsed = null;
                fileData = null;
            }
        });
    }

    private void onFile(String name, byte[] data) {
        fileName = name;
        fileData = data;
        format.setValue(SecretFormat.fromFileName(name));
        parse();
    }

    private void parse() {
        try {
            parsed = codec.readGroup(new String(fileData, java.nio.charset.StandardCharsets.UTF_8), format.getValue());
        } catch (IllegalArgumentException ex) {
            parsed = null;
            refreshPreview();
            UiSupport.error(ex.getMessage());
            return;
        }
        refreshPreview();
    }

    private void clear() {
        parsed = null;
        fileName = null;
        fileData = null;
        refreshPreview();
    }

    private String status(Row row) {
        if (!row.exists()) {
            return "create";
        }
        return overwrite.getValue() ? "overwrite" : "skip (exists)";
    }

    private void refreshPreview() {
        warnings.removeAll();
        if (parsed == null) {
            preview.setVisible(false);
            summary.setText("");
            importButton.setEnabled(false);
            return;
        }
        Set<String> existing = Set.of();
        String g = group.getValue().trim();
        boolean groupValid = true;
        try {
            Names.group(g);
            existing = secrets.existingKeys(g);
        } catch (IllegalArgumentException ex) {
            groupValid = false;
        }
        Set<String> finalExisting = existing;
        List<Row> rows = parsed.entries().entrySet().stream()
                .map(e -> new Row(e.getKey(), e.getValue().length(), finalExisting.contains(e.getKey())))
                .toList();
        preview.setItems(rows);
        preview.setVisible(!rows.isEmpty());

        long willStore = rows.stream().filter(r -> !r.exists() || overwrite.getValue()).count();
        summary.setText(fileName + ": " + rows.size() + " entries, " + willStore + " will be stored"
                + (groupValid ? "" : ". Enter a valid group name first."));
        parsed.warnings().forEach(w -> {
            Span s = new Span(w);
            s.addClassName("muted");
            warnings.add(s);
        });
        importButton.setText("Import " + willStore + " secret" + (willStore == 1 ? "" : "s"));
        importButton.setEnabled(groupValid && willStore > 0);
    }

    private void runImport() {
        String g = group.getValue().trim();
        try {
            Map<String, String> entries = parsed.entries();
            SecretImportService.Result result = importer.importEntries(g, entries, overwrite.getValue(),
                    "admin:" + admin, parsed.warnings());
            audit.record(ActorType.ADMIN, admin, "IMPORT", g, Outcome.SUCCESS,
                    "file=" + fileName + " format=" + format.getValue().id() + " created=" + result.created() + " updated=" + result.updated()
                            + " skipped=" + result.skippedExisting());
            String message = "Imported into " + g + ": " + result.created() + " created, " + result.updated()
                    + " updated, " + result.skippedExisting() + " skipped";
            if (result.warnings().size() > parsed.warnings().size()) {
                UiSupport.error(message + ". Problems: "
                        + String.join("; ", result.warnings().subList(parsed.warnings().size(), result.warnings().size())));
            } else {
                UiSupport.success(message);
            }
            parsed = null;
            close();
            onImported.run();
        } catch (RuntimeException ex) {
            UiSupport.error(ex.getMessage());
        }
    }
}
