package tech.sarthakgoyal.bastionvault.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.vaadin.flow.server.VaadinRequest;

/**
 * Remote address of the current request. Behind Traefik this is the real client, because Tomcat's
 * RemoteIpValve resolves X-Forwarded-For for proxies listed in VAULT_TRUSTED_PROXIES.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String current() {
        VaadinRequest vaadinRequest = VaadinRequest.getCurrent();
        if (vaadinRequest != null) {
            return vaadinRequest.getRemoteAddr();
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return "unknown";
    }
}
