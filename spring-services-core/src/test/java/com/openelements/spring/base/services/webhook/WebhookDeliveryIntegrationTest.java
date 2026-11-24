package com.openelements.spring.base.services.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.tag.TagDataService;
import com.openelements.spring.base.services.tag.TagDto;
import com.openelements.spring.base.services.tag.TagRepository;
import com.openelements.spring.base.services.webhook.data.WebhookDataService;
import com.openelements.spring.base.services.webhook.data.WebhookEntity;
import com.openelements.spring.base.services.webhook.data.WebhookRepository;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end tests for the delivery of data events to webhooks against a real Postgres database.
 *
 * <h2>What is tested</h2>
 *
 * <p>That a data event is delivered on the webhook executor, not on the thread that saved the
 * data: {@code save(...)} returns while the receiver is still holding the call, and the call's
 * status is recorded on the webhook row once the receiver answers. Also that the dedicated
 * executor does not replace Spring Boot's {@code applicationTaskExecutor}.
 *
 * <h2>How it is tested</h2>
 *
 * <p>The webhook row is written through the repository, so that creating it does not itself
 * trigger a webhook call.
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * starts a real Postgres via Testcontainers. The receiver is a JDK {@link HttpServer} whose
 * handler blocks until the test releases it, so the test does not depend on timing: if the call
 * ran on the saving thread, {@code save(...)} would block until the bounded timeout fails the
 * test.
 *
 * <p><b>Mock-Audit.</b> {@code AuthService} is the single {@code @MockitoBean}, for the audit
 * listener that also reacts to the tag events. Data services, repositories, the webhook sender and
 * its executor are real beans.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("Webhook delivery integration")
class WebhookDeliveryIntegrationTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @Autowired private TagDataService tagDataService;

  @Autowired private TagRepository tagRepository;

  @Autowired private WebhookDataService webhookDataService;

  @Autowired private WebhookRepository webhookRepository;

  @Autowired private ApplicationContext applicationContext;

  @MockitoBean private AuthService authService;

  private HttpServer receiver;

  private final CountDownLatch received = new CountDownLatch(1);

  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void setUp() throws IOException {
    tagRepository.deleteAll();
    webhookRepository.deleteAll();
    receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    receiver.createContext(
        "/hook",
        exchange -> {
          received.countDown();
          try {
            release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    receiver.start();
  }

  @AfterEach
  void tearDown() {
    release.countDown();
    receiver.stop(0);
  }

  @Test
  @DisplayName("save(...) returns while the webhook receiver still holds the call; the status is recorded once it answers.")
  void shouldDeliverWithoutBlockingTheSave() throws InterruptedException {
    // GIVEN
    final WebhookEntity webhook = new WebhookEntity();
    webhook.setUrl("http://localhost:" + receiver.getAddress().getPort() + "/hook");
    webhook.setActive(true);
    final UUID webhookId = webhookRepository.save(webhook).getId();

    // WHEN
    assertTimeoutPreemptively(
        TIMEOUT, () -> tagDataService.save(new TagDto(null, "tag", "description", "#aabbcc")));

    // THEN
    assertThat(received.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    assertThat(webhookDataService.findById(webhookId).orElseThrow().lastStatus()).isNull();
    release.countDown();
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(webhookDataService.findById(webhookId).orElseThrow().lastStatus())
                    .isEqualTo(204));
  }

  @Test
  @DisplayName("The webhook executor sits next to Spring Boot's applicationTaskExecutor instead of replacing it.")
  void shouldKeepTheApplicationTaskExecutor() {
    assertThat(applicationContext.containsBean(WebhookConfig.EXECUTOR_BEAN_NAME)).isTrue();
    assertThat(applicationContext.containsBean("applicationTaskExecutor")).isTrue();
  }
}
