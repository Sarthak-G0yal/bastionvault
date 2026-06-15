package tech.sarthakgoyal.bastionvault.ui.views;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.model.SecretDocument;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;
import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Notice the layout parameter here! It tells Vaadin to wrap this inside the sidebar.
@Route(value = "", layout = MainLayout.class)
@PageTitle("Secrets Dashboard | Bastion")
public class SecretsDashboardView extends VerticalLayout {

    private final SecretRepository secretRepository;
    private final VaultSessionManager sessionManager;
    private final Accordion groupsAccordion = new Accordion();

    public SecretsDashboardView(SecretRepository secretRepository, VaultSessionManager sessionManager) {
        this.secretRepository = secretRepository;
        this.sessionManager = sessionManager;

        if (!sessionManager.isUnlocked()) {
            getUI().ifPresent(ui -> ui.navigate("unlock"));
            return;
        }

        setSizeFull();
        add(new H2("Infrastructure Secrets"));

        // 1. The Entry Form
        add(createAddSecretForm());

        // 2. The Grouped Accordion Display
        groupsAccordion.setWidthFull();
        add(groupsAccordion);
        refreshAccordion();
    }

    private FormLayout createAddSecretForm() {
        TextField groupField = new TextField("App Group (e.g., vaultwarden)");
        TextField keyField = new TextField("Secret Key (e.g., DB_PASS)");
        PasswordField valueField = new PasswordField("Plaintext Value");

        // Crucial: Keep Bitwarden away from overriding these specific fields
        valueField.getElement().executeJs("this.inputElement.setAttribute('data-bwignore', 'true');");

        Button saveButton = new Button("Encrypt & Save", e -> {
            // Notice we removed groupField.isEmpty() from the error check!
            if (keyField.isEmpty() || valueField.isEmpty()) {
                Notification.show("Key and Value fields are required.");
                return;
            }

            // If the user leaves the group blank, default it to "Ungrouped"
            String groupName = groupField.isEmpty() ? "Ungrouped" : groupField.getValue();

            saveEncryptedSecret(groupName, keyField.getValue(), valueField.getValue());

            groupField.clear();
            keyField.clear();
            valueField.clear();
            refreshAccordion();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(groupField, keyField, valueField, saveButton);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 4));
        return form;
    }

    private void saveEncryptedSecret(String group, String key, String plaintext) {
        try {
            // Generate a secure, random IV for this specific secret
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Initialize AES Cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, sessionManager.getActiveKey(), ivSpec);

            // Encrypt and encode
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
            String encodedCiphertext = Base64.getEncoder().encodeToString(encrypted);
            String encodedIv = Base64.getEncoder().encodeToString(iv);

            // Save to MongoDB
            SecretDocument doc = new SecretDocument();
            doc.setKey(key);
            doc.setApplicationGroup(group);
            doc.setCiphertext(encodedCiphertext);
            doc.setIv(encodedIv);
            doc.setLastUpdated(LocalDateTime.now());
            secretRepository.save(doc);

            Notification.show("Secret encrypted and saved securely.");
        } catch (Exception ex) {
            Notification.show("Encryption Failed: " + ex.getMessage());
        }
    }

    private void refreshAccordion() {
        groupsAccordion.getChildren().toList().forEach(groupsAccordion::remove);
        List<SecretDocument> allSecrets = secretRepository.findAll();

        // Group secrets by their applicationGroup field dynamically
        Map<String, List<SecretDocument>> groupedSecrets = allSecrets.stream()
                .collect(Collectors.groupingBy(
                        doc -> doc.getApplicationGroup() != null ? doc.getApplicationGroup() : "Ungrouped"));

        groupedSecrets.forEach((groupName, secretsList) -> {
            Grid<SecretDocument> grid = new Grid<>(SecretDocument.class, false);
            grid.addColumn(SecretDocument::getKey).setHeader("Key");
            grid.addColumn(doc -> "••••••••••••").setHeader("Encrypted Value"); // Never show plaintext in UI
            grid.addColumn(SecretDocument::getLastUpdated).setHeader("Last Updated");

            // Add a delete button to the grid
            grid.addComponentColumn(secret -> {
                Button deleteBtn = new Button("Delete", click -> {
                    secretRepository.delete(secret);
                    refreshAccordion();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                return deleteBtn;
            });

            grid.setItems(secretsList);
            grid.setAllRowsVisible(true); // Let the accordion handle the height

            groupsAccordion.add(groupName + " (" + secretsList.size() + " keys)", grid);
        });
    }
}