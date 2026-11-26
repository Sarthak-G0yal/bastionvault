package tech.sarthakgoyal.bastionvault.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;

import tech.sarthakgoyal.bastionvault.ui.layout.MainLayout;
import tech.sarthakgoyal.bastionvault.ui.views.AuditView;
import tech.sarthakgoyal.bastionvault.ui.views.ClientsView;
import tech.sarthakgoyal.bastionvault.ui.views.LoginView;
import tech.sarthakgoyal.bastionvault.ui.views.SecretsView;
import tech.sarthakgoyal.bastionvault.ui.views.SettingsView;
import tech.sarthakgoyal.bastionvault.ui.views.SetupView;
import tech.sarthakgoyal.bastionvault.ui.views.UnsealView;

/** Guards the view/layout annotations: admins reach every page, anonymous users only login/setup. */
class ViewAccessTest {

    private final AccessAnnotationChecker checker = new AccessAnnotationChecker();
    private final Principal admin = () -> "admin";

    private static final List<Class<?>> PROTECTED = List.of(SecretsView.class, ClientsView.class, AuditView.class,
            SettingsView.class, UnsealView.class);

    @Test
    void adminCanOpenEveryProtectedViewAndItsLayout() {
        for (Class<?> view : PROTECTED) {
            assertThat(checker.hasAccess(view, admin, role -> role.equals("ADMIN"))).as(view.getSimpleName()).isTrue();
            assertThat(view.getAnnotation(Route.class).layout()).isEqualTo(MainLayout.class);
        }
        assertThat(checker.hasAccess(MainLayout.class, admin, role -> role.equals("ADMIN"))).isTrue();
    }

    @Test
    void anonymousUsersOnlyReachLoginAndSetup() {
        for (Class<?> view : PROTECTED) {
            assertThat(checker.hasAccess(view, null, role -> false)).as(view.getSimpleName()).isFalse();
        }
        assertThat(checker.hasAccess(MainLayout.class, null, role -> false)).isFalse();
        assertThat(checker.hasAccess(LoginView.class, null, role -> false)).isTrue();
        assertThat(checker.hasAccess(SetupView.class, null, role -> false)).isTrue();
    }

    @Test
    void nonAdminRolesAreDenied() {
        for (Class<?> view : PROTECTED) {
            assertThat(checker.hasAccess(view, () -> "machine", role -> role.equals("CLIENT")))
                    .as(view.getSimpleName()).isFalse();
        }
    }
}
