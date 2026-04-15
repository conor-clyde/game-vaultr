package com.cocoding.playstate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class IgdbTokenService {

    private static final Logger logger = LoggerFactory.getLogger(IgdbTokenService.class);
    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";

    @Value("${igdb.client.id}")
    private String clientId;

    @Value("${igdb.client.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private String accessToken;
    private long tokenExpiryTimeEpochMs;

    public String getClientId() {
        return clientId;
    }

    
    public synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryTimeEpochMs) {
            return accessToken;
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new RuntimeException(
                    "IGDB credentials are missing. Set IGDB_CLIENT_ID and IGDB_CLIENT_SECRET (or TWITCH_* variants).");
        }

        String requestBody =
                "client_id=" + urlEncode(clientId)
                        + "&client_secret=" + urlEncode(clientSecret)
                        + "&grant_type=" + urlEncode("client_credentials");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(TOKEN_URL, entity, Map.class);
            if (response == null) {
                logger.error("Failed to obtain IGDB access token - Response: null");
                throw new RuntimeException("Failed to obtain IGDB access token - Response: null");
            }

            Object tokenObj = response.get("access_token");
            if (tokenObj instanceof String token) {
                accessToken = token;
                logger.info("Successfully obtained IGDB access token");

                Object expiresObj = response.get("expires_in");
                long expiresInMs = (expiresObj instanceof Number)
                        ? ((Number) expiresObj).longValue() * 1000
                        : 30L * 24 * 60 * 60 * 1000;
                tokenExpiryTimeEpochMs = System.currentTimeMillis() + expiresInMs;
                return accessToken;
            }
            logger.error("Failed to obtain IGDB access token - Response: {}", response);
            throw new RuntimeException("Failed to obtain IGDB access token - Response: " + response);
        } catch (HttpClientErrorException e) {
            logger.error("Failed to obtain IGDB access token - HTTP error: {} - Response: {}", e.getStatusCode(),
                    e.getResponseBodyAsString(), e);
            throw new RuntimeException(
                    "Failed to obtain IGDB access token: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    e);
        } catch (Exception e) {
            logger.error("Failed to obtain IGDB access token", e);
            throw new RuntimeException("Failed to obtain IGDB access token", e);
        }
    }

    public synchronized void invalidateAccessToken() {
        accessToken = null;
        tokenExpiryTimeEpochMs = 0L;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
