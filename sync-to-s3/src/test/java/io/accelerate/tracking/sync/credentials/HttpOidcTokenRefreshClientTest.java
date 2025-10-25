package io.accelerate.tracking.sync.credentials;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.*;

class HttpOidcTokenRefreshClientTest {

    @Test
    void shouldExchangeTokenAgainstRefreshEndpoint() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\":\"new-token\",\"token_type\":\"Bearer\",\"expires_in\":298}");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(requestCaptor.capture(), Mockito.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        String issuer = "https://issuer.example";
        String token = tokenWithIssuer(issuer);

        OidcTokenRefreshClient refreshClient = new HttpOidcTokenRefreshClient(httpClient);

        String refreshedToken = refreshClient.refresh(token);

        Assertions.assertEquals("new-token", refreshedToken);

        HttpRequest capturedRequest = requestCaptor.getValue();
        Assertions.assertEquals("POST", capturedRequest.method());
        Assertions.assertEquals(issuer + "/refresh/token", capturedRequest.uri().toString());
        Assertions.assertEquals("Bearer " + token, capturedRequest.headers().firstValue("Authorization").orElse(null));

        verify(httpClient, times(1)).send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any());
        verifyNoMoreInteractions(httpClient);
    }

    private static String tokenWithIssuer(String issuer) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"iss\":\"" + issuer + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
