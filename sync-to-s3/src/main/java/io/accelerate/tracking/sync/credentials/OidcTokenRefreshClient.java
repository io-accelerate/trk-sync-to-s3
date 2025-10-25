package io.accelerate.tracking.sync.credentials;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

public interface OidcTokenRefreshClient {
    String refresh(String originalOidcToken);
}

class HttpOidcTokenRefreshClient implements OidcTokenRefreshClient {
    private static final Logger log = getLogger(HttpOidcTokenRefreshClient.class);
    private static final Pattern ISS_CLAIM_PATTERN = Pattern.compile("\"iss\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;

    HttpOidcTokenRefreshClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public String refresh(String originalOidcToken) {
        Objects.requireNonNull(originalOidcToken, "originalOidcToken");
        String issuer;
        try {
            issuer = extractIssuer(originalOidcToken);
        } catch (RuntimeException e) {
            log.error("Failed to determine issuer while refreshing OIDC token", e);
            throw e;
        }

        URI refreshUri = buildRefreshUri(issuer);
        log.info("Refreshing OIDC token issued by '{}'", issuer);

        HttpRequest request = HttpRequest.newBuilder(refreshUri)
                .header("Authorization", "Bearer " + originalOidcToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request, issuer);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Failed to refresh OIDC token for issuer '{}'. HTTP status {}", issuer, response.statusCode());
            throw new IllegalStateException("Failed to refresh OIDC token. HTTP status: " + response.statusCode());
        }

        String accessToken = extractAccessToken(response.body());
        if (accessToken == null || accessToken.isBlank()) {
            log.error("IDP refresh response for issuer '{}' did not contain an access token", issuer);
            throw new IllegalStateException("IDP refresh response missing access token");
        }
        log.info("Successfully refreshed OIDC token for issuer '{}'", issuer);
        return accessToken;
    }

    private HttpResponse<String> send(HttpRequest request, String issuer) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while refreshing OIDC token for issuer '{}'", issuer, e);
            throw new IllegalStateException("Interrupted while refreshing OIDC token", e);
        } catch (IOException e) {
            log.error("I/O error while refreshing OIDC token for issuer '{}': {}", issuer, e.getMessage(), e);
            throw new IllegalStateException("Failed to refresh OIDC token: " + e.getMessage(), e);
        }
    }

    private static String extractIssuer(String originalOidcToken) {
        String[] tokenParts = originalOidcToken.split("\\.");
        if (tokenParts.length < 2) {
            throw new IllegalStateException("OIDC token missing payload");
        }

        String payloadJson;
        try {
            byte[] decodedPayload = Base64.getUrlDecoder().decode(tokenParts[1]);
            payloadJson = new String(decodedPayload, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.error("OIDC token payload was not valid base64url while attempting refresh", e);
            throw new IllegalStateException("OIDC token payload is not valid base64url", e);
        }

        Matcher matcher = ISS_CLAIM_PATTERN.matcher(payloadJson);
        if (!matcher.find()) {
            log.error("OIDC token payload did not contain an issuer claim while attempting refresh");
            throw new IllegalStateException("OIDC token does not contain issuer claim");
        }
        return matcher.group(1);
    }

    private static URI buildRefreshUri(String issuer) {
        String base = issuer.endsWith("/") ? issuer : issuer + "/";
        return URI.create(base + "refresh/token");
    }

    private static String extractAccessToken(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }
}
