/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Decides whether an {@code Authorization} credential may be sent over a given connection.
 *
 * <p>{@code https} and {@code wss} are always permitted, as are cleartext connections to a loopback
 * address, where the traffic never leaves the local host. Cleartext connections to any other host -
 * such as an in-cluster {@code http://cryostat.namespace.svc:8181} Service URL - expose the
 * credential to anything able to observe the network path, and are only permitted when the
 * deployment explicitly opts in via {@link #ALLOW_INSECURE_CREDENTIALS_PROPERTY} or the {@link
 * #ALLOW_INSECURE_CREDENTIALS_ENV} environment variable.
 *
 * <p>Unauthenticated cleartext endpoints are unaffected: with no credential to leak there is
 * nothing to protect.
 */
public final class CredentialTransportPolicy {

    public static final String ALLOW_INSECURE_CREDENTIALS_PROPERTY =
            "cryostat.mcp.allow-insecure-credentials";
    public static final String ALLOW_INSECURE_CREDENTIALS_ENV =
            "CRYOSTAT_ALLOW_INSECURE_CREDENTIALS";

    private static final Pattern IPV4_LOOPBACK =
            Pattern.compile("^127(?:\\.(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}$");

    private CredentialTransportPolicy() {}

    /** Whether a credential may be sent to the given URI. */
    public static boolean permitsCredentials(URI uri) {
        return isSecureTransport(uri) || allowInsecureCredentials();
    }

    /**
     * Throw if {@code credential} may not be sent to {@code uri}. The credential itself is never
     * included in the message.
     */
    public static void requireSecureTransport(URI uri, String credential) throws IOException {
        if (credential == null || permitsCredentials(uri)) {
            return;
        }
        throw new IOException(violationMessage(uri));
    }

    /**
     * As {@link #requireSecureTransport(URI, String)}, for call sites that cannot propagate a
     * checked exception.
     */
    public static void requireSecureTransportUnchecked(URI uri, String credential) {
        if (credential == null || permitsCredentials(uri)) {
            return;
        }
        throw new IllegalStateException(violationMessage(uri));
    }

    public static String violationMessage(URI uri) {
        return "Refusing to send Authorization credentials over the cleartext connection to "
                + uri
                + ". Configure Cryostat with a TLS (https) URL, or set "
                + ALLOW_INSECURE_CREDENTIALS_ENV
                + "=true to accept the risk of exposing the credentials.";
    }

    private static boolean isSecureTransport(URI uri) {
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) {
            return true;
        }
        return isLoopback(uri.getHost());
    }

    private static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String bare =
                host.startsWith("[") && host.endsWith("]")
                        ? host.substring(1, host.length() - 1)
                        : host;
        return "localhost".equalsIgnoreCase(bare)
                || "::1".equals(bare)
                || IPV4_LOOPBACK.matcher(bare).matches();
    }

    private static boolean allowInsecureCredentials() {
        String property = System.getProperty(ALLOW_INSECURE_CREDENTIALS_PROPERTY);
        if (property == null) {
            property = System.getenv(ALLOW_INSECURE_CREDENTIALS_ENV);
        }
        return Boolean.parseBoolean(property);
    }
}
