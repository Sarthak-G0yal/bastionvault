package tech.sarthakgoyal.bastionvault.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;

// 1. Explicitly load the modern Aura base theme
@StyleSheet(Aura.STYLESHEET)
// 2. Load our native CSS directly
@StyleSheet("styles.css")
public class AppShell implements AppShellConfigurator {
}
