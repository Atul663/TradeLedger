package com.example.tradeLedger.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Translates Render's {@code DATABASE_URL} into the three properties this
 * application already reads.
 *
 * Render attaches a managed database to a service as a single libpq URL -
 * {@code postgresql://user:password@host:port/dbname} - which the JDBC driver
 * does not accept and which carries the credentials inline. Spring reads
 * {@code DB_URL}, {@code DB_USER} and {@code DB_PASSWORD}, so the split happens
 * here, before the context starts.
 *
 * <p>Explicitly set values always win. Nothing is overwritten, so a local
 * {@code .env}, docker-compose, or an externally managed database keeps working
 * exactly as before - this only fills a gap Render leaves.
 */
public final class DatabaseUrl {

    private static final String JDBC_PREFIX = "jdbc:postgresql://";

    private DatabaseUrl() {
    }

    /**
     * Fills {@code DB_URL} / {@code DB_USER} / {@code DB_PASSWORD} as system
     * properties from {@code DATABASE_URL} when they are not already set.
     *
     * <p>Call this before {@code SpringApplication.run}: system properties are
     * read ahead of the OS environment, so the placeholders in
     * application.properties resolve against whatever this leaves behind.
     */
    public static void applyIfPresent() {
        String databaseUrl = trimToNull(System.getenv("DATABASE_URL"));
        if (databaseUrl == null || isSet("DB_URL")) {
            return;
        }

        URI uri;
        try {
            uri = URI.create(databaseUrl);
        } catch (IllegalArgumentException malformed) {
            // Leave it alone rather than half-configure the datasource - the
            // startup failure on the unresolved ${DB_URL} names the real problem.
            return;
        }

        String host = uri.getHost();
        if (host == null) {
            return;
        }

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

        setIfAbsent("DB_URL", JDBC_PREFIX + host + ":" + port + "/" + database + query(uri, host));

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int separator = userInfo.indexOf(':');
            String user = separator < 0 ? userInfo : userInfo.substring(0, separator);
            String password = separator < 0 ? "" : userInfo.substring(separator + 1);
            setIfAbsent("DB_USER", decode(user));
            setIfAbsent("DB_PASSWORD", decode(password));
        }
    }

    /**
     * Keeps whatever the URL already carried, and adds {@code sslmode} when it
     * is absent.
     *
     * <p>Render's external hostnames are fully qualified and reject unencrypted
     * connections; the internal one is a bare label on the private network and
     * is left as the driver defaults it. {@code DB_SSL_MODE} overrides both.
     */
    private static String query(URI uri, String host) {
        String existing = trimToNull(uri.getQuery());
        if (existing != null && existing.contains("sslmode=")) {
            return "?" + existing;
        }

        String sslMode = trimToNull(System.getenv("DB_SSL_MODE"));
        if (sslMode == null) {
            sslMode = host.contains(".") ? "require" : null;
        }
        if (sslMode == null) {
            return existing == null ? "" : "?" + existing;
        }

        return existing == null ? "?sslmode=" + sslMode : "?" + existing + "&sslmode=" + sslMode;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isSet(String name) {
        return trimToNull(System.getProperty(name)) != null || trimToNull(System.getenv(name)) != null;
    }

    private static void setIfAbsent(String name, String value) {
        if (!isSet(name)) {
            System.setProperty(name, value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
