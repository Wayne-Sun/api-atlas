package com.api.atlas.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.SimpleJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchClientFactory implements DataSourceFactory<ElasticsearchClient> {

    private final JsonpMapper mapper = new SimpleJsonpMapper();

    @Value("${atlas.elasticsearch.protocol:http}")
    private String esProtocol;

    @Override
    public ElasticsearchClient createClient(String host, int port, String databaseName, String username, String password, String apiKey) throws Exception {
        HttpHost httpHost = new HttpHost(host, port, esProtocol);
        RestClientBuilder builder = RestClient.builder(httpHost)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(5000)
                        .setSocketTimeout(60000));

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "ApiKey " + apiKey)
            });
        }

        RestClient restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, mapper);
        return new ElasticsearchClient(transport);
    }

    @Override
    public void destroyClient(ElasticsearchClient client) {
        try {
            client._transport().close();
        } catch (Exception ignored) {
            // ignore close errors
        }
    }

    @Override
    public String getType() {
        return "Elasticsearch";
    }
}
