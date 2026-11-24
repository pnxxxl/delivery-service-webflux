package ru.mirea.delivery;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
@RestController
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
    public record Address(String city, String street, String building, String postalCode) {}
    public record Order(Long id, String customerName, Address address) {}
    public enum Status { CREATED, ACCEPTED, IN_TRANSIT, DELIVERED }
    public record Delivery(Long id, Long orderId, String recipient, Address address, Status status) {}
    public record Input(Long orderId, Status status) {}
    private final Map<Long, Delivery> data = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final WebClient orders;
    public Application(WebClient.Builder builder, @Value("${orders.base-url:http://localhost:8080}") String url) {
        orders = builder.baseUrl(url).build();
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"); }
    @GetMapping("/api/deliveries")
    public Flux<Delivery> all(@RequestParam(required=false) Status status) {
        return Flux.defer(() -> Flux.fromIterable(data.values())).filter(d -> status == null || d.status() == status);
    }
    @GetMapping("/api/deliveries/{id}")
    public Mono<Delivery> one(@PathVariable Long id) {
        return Mono.defer(() -> Mono.justOrEmpty(data.get(id))).switchIfEmpty(Mono.error(missing()));
    }
    private Mono<Order> order(Long id) {
        if (id == null || id < 1) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Positive orderId required"));
        return orders.get().uri("/api/orders/{id}", id).retrieve()
            .onStatus(s -> s.value() == 404, r -> Mono.just(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")))
            .bodyToMono(Order.class).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty order response")))
            .onErrorResume(e -> e instanceof ResponseStatusException ? Mono.error(e) : Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order service unavailable", e)));
    }
    @PostMapping("/api/deliveries") @ResponseStatus(HttpStatus.CREATED)
    public Mono<Delivery> create(@RequestBody Input input) {
        return order(input.orderId()).map(o -> new Delivery(ids.incrementAndGet(), o.id(), o.customerName(), o.address(), Status.CREATED))
            .doOnNext(d -> data.put(d.id(), d));
    }
    @PutMapping("/api/deliveries/{id}")
    public Mono<Delivery> update(@PathVariable Long id, @RequestBody Input input) {
        return one(id).flatMap(old -> order(input.orderId()).map(o -> new Delivery(id, o.id(), o.customerName(), o.address(), input.status() == null ? old.status() : input.status())))
            .flatMap(d -> Mono.justOrEmpty(data.replace(id, d)).map(old -> d).switchIfEmpty(Mono.error(missing())));
    }
    @DeleteMapping("/api/deliveries/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return Mono.defer(() -> Mono.justOrEmpty(data.remove(id))).switchIfEmpty(Mono.error(missing())).then();
    }
    // Учебный симулятор: подписка запускает последовательные реальные изменения записи.
    @GetMapping(value="/api/deliveries/{id}/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Delivery>> stream(@PathVariable Long id) {
        return one(id).flatMapMany(initial -> Flux.concat(Mono.just(initial),
            Flux.interval(Duration.ofSeconds(2)).take(3).concatMap(tick -> one(id).flatMap(current -> {
                Status next = Status.values()[Math.min(current.status().ordinal() + 1, 3)];
                Delivery changed = new Delivery(id, current.orderId(), current.recipient(), current.address(), next);
                return Mono.justOrEmpty(data.replace(id, changed)).map(old -> changed).switchIfEmpty(Mono.error(missing()));
            }))))
            .map(d -> ServerSentEvent.<Delivery>builder(d).event(d.status().name()).build());
    }
    // Локальная HTTP-заглушка Order Service; WebClient действительно делает сетевой запрос.
    @GetMapping("/api/orders/{id}")
    public Mono<Order> demoOrder(@PathVariable Long id) {
        return Mono.just(id).filter(n -> n > 0 && n <= 3)
            .map(n -> new Order(n, "Daria", new Address("Moscow", "Vernadskogo", "78", "119454")))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }
}
