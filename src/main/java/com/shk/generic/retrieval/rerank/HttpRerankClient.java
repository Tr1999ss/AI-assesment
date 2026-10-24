package com.shk.generic.retrieval.rerank;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Minimal JSON POST client for rerank APIs, with connect and read timeouts.
 * Errors are reduced to status codes: response bodies (which may echo request data) and the API key
 * never reach logs or callers.
 */
final class HttpRerankClient {

    private final RestClient client;
    private final String name;

    HttpRerankClient(String name, String baseUrl, Duration timeout) {
        this.name = name;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    <T> T post(String path, String bearerToken, Object body, Class<T> responseType) {
        try {
            RestClient.RequestBodySpec request = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (bearerToken != null && !bearerToken.isBlank()) {
                request = request.header("Authorization", "Bearer " + bearerToken);
            }
            T response = request.body(body).retrieve().body(responseType);
            if (response == null) {
                throw new RerankException(name + ": empty response");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new RerankException(name + ": HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new RerankException(name + ": request failed (" + rootCause(e).getClass().getSimpleName() + ")");
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
