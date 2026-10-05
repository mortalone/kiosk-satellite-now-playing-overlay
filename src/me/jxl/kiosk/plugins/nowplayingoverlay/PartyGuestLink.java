// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.nowplayingoverlay;

import java.net.URI;

final class PartyGuestLink {
    private PartyGuestLink() {}

    static boolean matches(String selectedQueue, Object partyPlayer) {
        return selectedQueue != null && !selectedQueue.trim().isEmpty() &&
                partyPlayer instanceof String && selectedQueue.equals(((String) partyPlayer).trim());
    }

    static String validated(String raw, String server) {
        if (raw == null || raw.length() > 2048) return "";
        try {
            URI url = new URI(raw);
            String scheme = url.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) ||
                    url.getHost() == null || url.getUserInfo() != null || url.getFragment() != null) return "";
            String query = url.getRawQuery();
            boolean join = false;
            if (query == null) return "";
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                String key = java.net.URLDecoder.decode(pair[0], "UTF-8");
                if ("join".equals(key) && pair.length == 2 && !pair[1].isEmpty()) join = true;
                if ("auth_token".equalsIgnoreCase(key) || "token".equalsIgnoreCase(key)) return "";
            }
            if (!join) return "";
            // MA 2.9 can advertise localhost when no web base URL is configured.
            // Keep its own guest port/path, replacing only a loopback hostname.
            String host = url.getHost();
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host)) {
                URI base = new URI(server);
                String remote = base.getHost();
                if (remote == null || "localhost".equalsIgnoreCase(remote) || "127.0.0.1".equals(remote)) return "";
                String authority = remote.contains(":") && !remote.startsWith("[") ? "[" + remote + "]" : remote;
                if (url.getPort() >= 0) authority += ":" + url.getPort();
                return scheme + "://" + authority + (url.getRawPath() == null ? "/" : url.getRawPath()) + "?" + query;
            }
            return url.toASCIIString();
        } catch (Exception ignored) { return ""; }
    }
}
