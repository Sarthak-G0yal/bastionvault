package tech.sarthakgoyal.bastionvault.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

// This tells Vaadin to load /themes/bastion/styles.css while enforcing Dark Mode
@Theme(value = "bastion", variant = Lumo.DARK)
public class AppShell implements AppShellConfigurator {
}
