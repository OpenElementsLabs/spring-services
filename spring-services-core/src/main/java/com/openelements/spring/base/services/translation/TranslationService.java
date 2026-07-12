package com.openelements.spring.base.services.translation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service that proxies translation requests to an OpenAI-compatible chat completions API.
 *
 * <p>The feature is enabled when the environment variables {@code TRANSLATION_API_URL}, {@code
 * TRANSLATION_API_KEY} and {@code TRANSLATION_MODEL} are all set. If any of these is missing the
 * service is considered unconfigured and {@link #translate(String, Language)} will throw a {@code
 * 503 Service Unavailable}. The frontend is expected to call {@link #isConfigured()} via the {@code
 * /api/translate/settings} endpoint to decide whether to expose translate buttons.
 */
@Service
public class TranslationService {

  private static final Logger LOG = LoggerFactory.getLogger(TranslationService.class);

  private final String apiUrl;
  private final String apiKey;
  private final String model;
  private final boolean configured;
  private final RestClient restClient;

  /**
   * Creates a new TranslationService.
   *
   * @param apiUrl base URL of the OpenAI-compatible API ({@code TRANSLATION_API_URL})
   * @param apiKey bearer token for the API ({@code TRANSLATION_API_KEY})
   * @param model model identifier for chat completions ({@code TRANSLATION_MODEL})
   */
  public TranslationService(
      @Value("${translation.api-url:}") final String apiUrl,
      @Value("${translation.api-key:}") final String apiKey,
      @Value("${translation.model:}") final String model) {
    this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.configured = !this.apiUrl.isEmpty() && !this.apiKey.isEmpty() && !this.model.isEmpty();
    this.restClient = configured ? RestClient.builder().baseUrl(this.apiUrl).build() : null;
    if (!configured) {
      LOG.warn(
          "Translation feature is not configured: set TRANSLATION_API_URL, "
              + "TRANSLATION_API_KEY and TRANSLATION_MODEL to enable translate buttons");
    } else {
      LOG.info("Translation feature configured with model '{}' at {}", this.model, this.apiUrl);
    }
  }

  /**
   * Returns whether all required translation environment variables are configured.
   *
   * @return true if the service can perform translations
   */
  public boolean isConfigured() {
    return configured;
  }

  /**
   * Translates the given text into the given target language by calling the OpenAI-compatible
   * {@code /chat/completions} endpoint.
   *
   * @param text the source text — must not be blank
   * @param targetLanguage the target language ({@code de} or {@code en})
   * @return the translated text
   * @throws ResponseStatusException 503 if the service is not configured, 502 if the upstream call
   *     fails or returns an unparseable response
   */
  public String translate(final String text, final Language targetLanguage) {
    if (!configured) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Translation is not configured");
    }
    final String languageName =
        switch (targetLanguage) {
          case DE -> "German";
          case EN -> "English";
        };

    final Map<String, Object> body =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of(
                    "role",
                    "system",
                    "content",
                    "You are a translator. Translate the following text to "
                        + languageName
                        + ". Return only the translated text, no explanations."),
                Map.of("role", "user", "content", text)));

    final JsonNode response;
    try {
      response =
          restClient
              .post()
              .uri("/chat/completions")
              .header("Authorization", "Bearer " + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);
    } catch (final Exception e) {
      LOG.warn("Translation API call failed: {}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Translation API call failed");
    }

    if (response == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Translation API returned an empty response");
    }
    final JsonNode choices = response.get("choices");
    if (choices == null || !choices.isArray() || choices.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Translation API response missing choices");
    }
    final JsonNode message = choices.get(0).get("message");
    if (message == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Translation API response missing message");
    }
    final JsonNode content = message.get("content");
    if (content == null || !content.isTextual()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Translation API response missing content");
    }
    return content.asText();
  }
}
