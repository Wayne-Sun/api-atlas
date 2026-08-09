package com.api.atlas.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class MongoClientFactory implements DataSourceFactory<MongoClient> {

    private final HostSecurityValidator hostSecurityValidator;
    private final int connectTimeoutMs;
    private final int serverSelectionTimeoutMs;
    private final int socketTimeoutMs;

    public MongoClientFactory(HostSecurityValidator hostSecurityValidator,
                              @Value("${atlas.mongodb.connect-timeout-ms:5000}") int connectTimeoutMs,
                              @Value("${atlas.mongodb.server-selection-timeout-ms:5000}") int serverSelectionTimeoutMs,
                              @Value("${atlas.mongodb.socket-timeout-ms:60000}") int socketTimeoutMs) {
        this.hostSecurityValidator = hostSecurityValidator;
        this.connectTimeoutMs = connectTimeoutMs;
        this.serverSelectionTimeoutMs = serverSelectionTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
    }

    @Override
    public MongoClient createClient(String host, int port, String databaseName, String username, String password, String apiKey) {
        ConnectionString cs = buildConnectionString(host, port, username, password);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(cs)
                .build();
        // Lazy connection — creation does not touch the network; the driver
        // connects on the first operation only.
        return MongoClients.create(settings);
    }

    @Override
    public void destroyClient(MongoClient client) {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public String getType() {
        return "MongoDB";
    }

    /**
     * Builds a MongoDB connection string.
     *
     * <p>If {@code host} is already a full connection string (starts with
     * {@code mongodb://} or {@code mongodb+srv://}), it is parsed and validated:
     * URIs embedding credentials (an {@code @} inside the authority) are rejected
     * so credentials flow only through the db fields, and every host in the URI is
     * checked against {@link HostSecurityValidator}. Otherwise a standard URI is
     * built, embedding credentials only when BOTH username and password are
     * present. Username/password are percent-encoded so that special characters
     * like {@code @}, {@code :} and {@code /} inside passwords do not break URI
     * parsing.</p>
     *
     * <p>Package-visible so unit tests can assert on the result directly.</p>
     */
    ConnectionString buildConnectionString(String host, int port, String username, String password) {
        if (host.startsWith("mongodb://") || host.startsWith("mongodb+srv://")) {
            return parseAndValidateFullUri(host);
        }

        if (hostSecurityValidator.isBlocked(host)) {
            throw new IllegalArgumentException("Host not allowed");
        }

        StringBuilder uri = new StringBuilder("mongodb://");

        boolean hasCredentials = username != null && !username.isEmpty()
                && password != null && !password.isEmpty();
        if (hasCredentials) {
            uri.append(encode(username))
                    .append(':')
                    .append(encode(password))
                    .append('@');
        }

        uri.append(host).append(':').append(port)
                .append("/?authSource=admin")
                .append("&connectTimeoutMS=").append(connectTimeoutMs)
                .append("&serverSelectionTimeoutMS=").append(serverSelectionTimeoutMs)
                .append("&socketTimeoutMS=").append(socketTimeoutMs);

        return new ConnectionString(uri.toString());
    }

    /**
     * Validates a full MongoDB URI passed as {@code host}.
     *
     * <p>Rejects URIs that embed credentials (a literal {@code @} inside the
     * authority, i.e. before the first {@code /} after the scheme) so credentials
     * flow only through the db fields, then runs every {@link
     * ConnectionString#getHosts() host} (including the port) through {@link
     * HostSecurityValidator}. Throws {@link IllegalArgumentException} on
     * rejection — never touches the network.</p>
     */
    private ConnectionString parseAndValidateFullUri(String host) {
        String scheme = host.startsWith("mongodb+srv://") ? "mongodb+srv://" : "mongodb://";
        if (hasEmbeddedCredentials(host, scheme)) {
            throw new IllegalArgumentException("Credentials must not be embedded in the connection string");
        }

        ConnectionString cs = new ConnectionString(host);
        for (String hostEntry : cs.getHosts()) {
            if (hostSecurityValidator.isBlocked(hostPartOf(hostEntry))) {
                throw new IllegalArgumentException("Host not allowed");
            }
        }
        return cs;
    }

    private static boolean hasEmbeddedCredentials(String host, String scheme) {
        String authority = host.substring(scheme.length());
        int slash = authority.indexOf('/');
        if (slash >= 0) {
            authority = authority.substring(0, slash);
        }
        return authority.contains("@");
    }

    /**
     * Extracts the host portion of a {@code ConnectionString.getHosts()} entry,
     * which is normally {@code host:port} (or {@code [ipv6]:port}); {@code
     * mongodb+srv} hosts carry no port.
     */
    private static String hostPartOf(String hostEntry) {
        if (hostEntry.startsWith("[")) {
            int close = hostEntry.indexOf(']');
            if (close > 0) {
                return hostEntry.substring(1, close);
            }
        }
        int colon = hostEntry.lastIndexOf(':');
        if (colon > 0) {
            return hostEntry.substring(0, colon);
        }
        return hostEntry;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
