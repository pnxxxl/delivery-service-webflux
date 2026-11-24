# Практическая работа 7 — Delivery WebFlux

Java 17+, Maven 3.9+. Запуск: `mvn spring-boot:run`. Адрес: http://localhost:8080.
Проверка: `mvn test`. Данные хранятся в памяти и сбрасываются после перезапуска.

## Проверка HTTP и SSE
В PowerShell используйте именно curl.exe:
```powershell
curl.exe -i -H 'Content-Type: application/json' -d '{\"orderId\":1}' http://localhost:8080/api/deliveries
curl.exe -i http://localhost:8080/api/deliveries
curl.exe -i http://localhost:8080/api/deliveries/1
curl.exe -N http://localhost:8080/api/deliveries/1/stream
curl.exe -i -X PUT -H 'Content-Type: application/json' -d '{\"orderId\":1,\"status\":\"ACCEPTED\"}' http://localhost:8080/api/deliveries/1
curl.exe -i -X DELETE http://localhost:8080/api/deliveries/1
curl.exe -i http://localhost:8080/api/deliveries/999
```
POST возвращает 201, DELETE — 204, отсутствующий объект — 404. Фильтрация: `/api/deliveries?status=CREATED`. Некорректный JSON/статус — 400. Недоступный Order Service — 502.

WebClient обращается по HTTP к `/api/orders/{id}`. По умолчанию демонстрационная заглушка размещена в том же приложении, существуют заказы 1–3. Для отдельного Order Service задайте `--orders.base-url=http://localhost:8081`. Используются поля customerName и address из практики №5.

SSE — учебная симуляция: подписка сначала возвращает текущее состояние, затем трижды с интервалом 2 секунды продвигает статус до DELIVERED и завершается. Изменения сохраняются в памяти. Повторная подписка начинает с текущего состояния; история и Last-Event-ID не реализованы. Это не универсальная шина уведомлений обо всех PUT. Несколько подписок могут совместно ускорить симуляцию.

## WebFlux и RSocket
1. WebFlux — веб-фреймворк на HTTP; RSocket — отдельный протокол взаимодействия. Mono и Flux являются типами Reactor, а не протоколами.
2. Request-Response соответствует HTTP GET с Mono: один запрос возвращает один объект.
3. Request-Stream соответствует HTTP GET с SSE и Flux: один запрос получает последовательность событий. SSE передаётся как text/event-stream.
4. RSocket поддерживает Fire-and-Forget и двунаправленный Channel. Обычный HTTP API не предоставляет эти модели непосредственно; POST имеет HTTP-ответ, SSE однонаправлен.
5. RSocket имеет протокольное управление спросом через REQUEST_N. SSE не передаёт такой сигнал от клиента; нельзя приравнивать HTTP flow control к RSocket backpressure.

В практике №4 RSocket использовал каталог поставщиков, поэтому буквального совпадения сущностей нет: здесь сохранена предметная область доставки из №5, а сравниваются модели взаимодействия.

Использованы map, filter, flatMap, switchIfEmpty и onErrorResume. В обработчиках нет block(). Интеграционный тест проверяет создание, чтение, изменение, фильтрацию, удаление и отсутствие заказа.

Для сдачи сделайте 2–4 снимка терминала с HTTP-ответами и отдельный снимок curl.exe -N с четырьмя событиями SSE.
