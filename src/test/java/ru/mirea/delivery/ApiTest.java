package ru.mirea.delivery;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
class ApiTest {
 @Autowired WebTestClient client;
 @Test void lifecycle() {
  client.post().uri("/api/deliveries").bodyValue(new Application.Input(1L,null)).exchange().expectStatus().isCreated().expectBody().jsonPath("$.status").isEqualTo("CREATED");
  client.get().uri("/api/deliveries/1").exchange().expectStatus().isOk();
  client.put().uri("/api/deliveries/1").bodyValue(new Application.Input(1L,Application.Status.ACCEPTED)).exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ACCEPTED");
  client.get().uri("/api/deliveries?status=DELIVERED").exchange().expectStatus().isOk().expectBody().json("[]");
  client.delete().uri("/api/deliveries/1").exchange().expectStatus().isNoContent();
  client.get().uri("/api/deliveries/1").exchange().expectStatus().isNotFound();
  client.post().uri("/api/deliveries").bodyValue(new Application.Input(99L,null)).exchange().expectStatus().isNotFound();
 }
}
