package tech.sarthakgoyal.bastionvault.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.aura.Aura;

@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
public class AppShell implements AppShellConfigurator {

    /**
     * Applies the theme before the first paint, so there is no light flash in dark mode: the choice
     * saved by the toggle wins, otherwise the system preference (followed live until the user picks).
     */
    static final String THEME_BOOTSTRAP = """
            (function () {
              var root = document.documentElement;
              var media = window.matchMedia('(prefers-color-scheme: dark)');
              function stored() { try { return localStorage.getItem('bv-theme'); } catch (e) { return null; } }
              function apply() { root.setAttribute('data-theme', stored() || (media.matches ? 'dark' : 'light')); }
              apply();
              media.addEventListener('change', function () { if (!stored()) { apply(); } });
            })();
            """;

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addInlineWithContents(Inline.Position.PREPEND, THEME_BOOTSTRAP, Inline.Wrapping.JAVASCRIPT);
    }
}
