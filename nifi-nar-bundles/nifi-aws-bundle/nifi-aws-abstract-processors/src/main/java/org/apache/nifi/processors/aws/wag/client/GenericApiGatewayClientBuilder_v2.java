package org.apache.nifi.processors.aws.wag.client;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.http.AmazonHttpClient;
import com.amazonaws.regions.Region;

public class GenericApiGatewayClientBuilder_v2 {
    private String endpoint;
    private Region region;
    private AWSCredentialsProvider credentials;
    private ClientConfiguration clientConfiguration;
    private String apiKey;
    private AmazonHttpClient httpClient;

    public GenericApiGatewayClientBuilder_v2 withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public GenericApiGatewayClientBuilder_v2 withRegion(Region region) {
        this.region = region;
        return this;
    }

    public GenericApiGatewayClientBuilder_v2 withClientConfiguration(ClientConfiguration clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
        return this;
    }

    public GenericApiGatewayClientBuilder_v2 withCredentials(AWSCredentialsProvider credentials) {
        this.credentials = credentials;
        return this;
    }

    public GenericApiGatewayClientBuilder_v2 withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public GenericApiGatewayClientBuilder_v2 withHttpClient(AmazonHttpClient client) {
        this.httpClient = client;
        return this;
    }

    public AWSCredentialsProvider getCredentials() {
        return credentials;
    }

    public String getApiKey() {
        return apiKey;
    }

    public AmazonHttpClient getHttpClient() {
        return httpClient;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Region getRegion() {
        return region;
    }

    public ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    public GenericApiGatewayClient_v2 build() {
        Validate.notEmpty(endpoint, "Endpoint");
        Validate.notNull(region, "Region");
        return new GenericApiGatewayClient_v2(clientConfiguration, endpoint, region, credentials, apiKey, httpClient);
    }

}
