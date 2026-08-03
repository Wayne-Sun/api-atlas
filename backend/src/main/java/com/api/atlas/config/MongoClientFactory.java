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

    @Value("${atlas.mongodb.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${atlas.mongodb.server-selection-timeout-ms:5000}")
    private int serverSelectionTimeoutMs;

    @Value("${atlas.mongodb.socket-timeout-ms:60000}")
    private int socketTimeoutMs;

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
     * {@code mongodb://} or {@code mongodb+srv://}), the whole string is passed
     * through untouched. Otherwise a standard URI is built, embedding credentials
     * only when BOTH username and password are present. Username/password are
     * percent-encoded so that special characters like {@code @}, {@code :} and
     * {@code /} inside passwords do not break URI parsing.</p>
     *
     * <p>Package-visible so unit tests can assert on the result directly.</p>
     */
    ConnectionString buildConnectionString(String host, int port, String username, String password) {
        if (host.startsWith("mongodb://") || host.startsWith("mongodb+srv://")) {
            return new ConnectionString(host);
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
