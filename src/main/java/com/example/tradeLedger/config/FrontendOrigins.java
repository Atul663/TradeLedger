package com.example.tradeLedger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The single list of browser origins this API trusts.
 *
 * Read from {@code app.cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS},
 * comma separated) so the Vercel domain is deployment configuration rather than
 * a constant to recompile. Entries may contain {@code *} as a wildcard within
 * one host or port - {@code https://*.vercel.app} covers preview deployments,
 * {@code http://localhost:*} covers any dev server port.
 *
 * <p>Both places that need the list use this one: {@link CorsConfig} to decide
 * which browsers may read a response, and the OAuth callback to decide where a
 * login may be sent back to. Keeping them on the same list is what stops the
 * {@code ?redirect=} parameter from being an open redirect - anyone can craft
 * that URL, and without the check Google's login would forward a freshly minted
 * session to an attacker's host.
 */
@Component
public class FrontendOrigins {

    private final List<String> patterns;
    private final List<Pattern> compiled;

    public FrontendOrigins(@Value("${app.cors.allowed-origins}") List<String> patterns) {
        this.patterns = patterns.stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
        this.compiled = this.patterns.stream().map(FrontendOrigins::toRegex).toList();
    }

    /** The configured patterns, in the form {@code allowedOriginPatterns} expects. */
    public String[] patterns() {
        return patterns.toArray(String[]::new);
    }

    /**
     * Whether {@code url}'s origin - scheme, host and port, path ignored - is on
     * the list. A malformed or non-absolute URL is never allowed.
     */
    public boolean allows(String url) {
        String origin = originOf(url);
        if (origin == null) {
            return false;
        }
        return compiled.stream().anyMatch(pattern -> pattern.matcher(origin).matches());
    }

    private static String originOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * {@code *} matches anything but a {@code /}, so a wildcard stays inside the
     * host or the port and cannot swallow the rest of a URL. Everything else is
     * quoted literally.
     */
    private static Pattern toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        String[] literals = pattern.split("\\*", -1);
        for (int i = 0; i < literals.length; i++) {
            if (i > 0) {
                regex.append("[^/]*");
            }
            if (!literals[i].isEmpty()) {
                regex.append(Pattern.quote(literals[i]));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
