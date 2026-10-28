package com.suwasarana.api.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client address used in audit rows and logs. It is the servlet's remote address: behind a proxy the container must
 * be configured to resolve the real client (server.forward-headers-strategy=native with a trusted-proxy list); we do
 * not read X-Forwarded-For ourselves because anyone can send that header.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
