# SafeBox Rental — interview-ready stack

Один сервис `application-service`, но он реально касается всего стека из легенды:
REST + JPA + Postgres + Liquibase + Kafka (producer + consumer) + Outbox + Idempotency + Redis cache + Actuator + Prometheus + Grafana + Swagger + traceId.

Этого хватит, чтобы потыкать руками каждый компонент и отвечать на собесе «у меня в проекте было так-то».

---

## 1. Запуск инфраструктуры

```bash
docker compose up -d
docker compose ps
```

Что поднялось и куда тыкать:

| Сервис | URL | Логин |
|---|---|---|
| Postgres | `localhost:5432` | safebox / safebox |
| Redis | `localhost:6379` | без пароля |
| Kafka | `localhost:9092` | brokerless для UI |
| Kafka UI | http://localhost:8090 | без пароля |
| Prometheus | http://localhost:9090 | без пароля |
| Grafana | http://localhost:3000 | admin / admin |

---

## 2. Запуск приложения

В отдельном терминале:
```bash
./gradlew :application-service:bootRun
```

Логи покажут `Started ApplicationServiceApplication`. Сервис на порту 8081.

Проверь жив:
```bash
curl -s http://localhost:8081/actuator/health
```

---

## 3. Пощупать REST + JPA + Liquibase

### 3.1. Создать заявку (POST)
```bash
curl -i -X POST http://localhost:8081/api/v1/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "clientId":"00000000-0000-0000-0000-000000000001",
    "cellId":"00000000-0000-0000-0000-0000000000aa",
    "rentalFrom":"2026-06-01",
    "rentalTo":"2026-06-30"
  }'
```
Получишь `201 Created`, в теле — `ApplicationResponse`. Сохрани `id`.

### 3.2. Проверить, что в БД
```bash
docker exec -it safebox-postgres psql -U safebox -d safebox -c "select id, status, version from applications;"
```
Заметь: `status=DRAFT`, `version=0`. Это **optimistic locking**, при апдейте version будет инкрементиться.

### 3.3. Получить заявку
```bash
curl -s http://localhost:8081/api/v1/applications/<ID>
```

### 3.4. Подтвердить заявку (переход статуса)
```bash
curl -i -X POST http://localhost:8081/api/v1/applications/<ID>/confirm
```
Статус → `WAITING_PAYMENT`. Проверь в БД, увидишь `version=1`.

### 3.5. Невалидный переход — error response
```bash
curl -i -X PATCH http://localhost:8081/api/v1/applications/<ID>/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"PAID"}'
```
Из `WAITING_PAYMENT` нельзя сразу в `PAID` минуя пайплайн. Получишь `409` и:
```json
{"code":"INVALID_STATUS_TRANSITION","message":"...","traceId":"...","timestamp":"..."}
```

### 3.6. Валидация (`@Valid`)
```bash
curl -i -X POST http://localhost:8081/api/v1/applications \
  -H 'Content-Type: application/json' -d '{}'
```
Получишь `400 VALIDATION_ERROR` с перечислением полей.

### 3.7. Liquibase
```bash
docker exec -it safebox-postgres psql -U safebox -d safebox -c "\dt"
docker exec -it safebox-postgres psql -U safebox -d safebox -c "select id, author, exectype from databasechangelog;"
```
Там увидишь `applications`, `processed_events`, `outbox_events`, и историю миграций в `databasechangelog`.

### 3.8. Swagger UI
http://localhost:8081/swagger-ui.html — все endpoints с моделями.

---

## 4. Пощупать Kafka + Outbox + Idempotency

### 4.1. Создай заявку (см 3.1). Что произошло
1. В одной транзакции записан Application **и** строка в `outbox_events` со status=PENDING.
2. Через 2 секунды `OutboxPublisher` (scheduler) публикует её в топик `application-events` и помечает SENT.

### 4.2. Проверь outbox
```bash
docker exec -it safebox-postgres psql -U safebox -d safebox -c \
  "select id, event_type, status, retry_count, sent_at from outbox_events;"
```
Должно быть `status=SENT`.

### 4.3. Зайди в Kafka UI
http://localhost:8090 → Topics → `application-events` → Messages. Увидишь сообщение, в key `applicationId` (партиционирование по заявке), в value JSON.

### 4.4. Спровоцируй consumer + idempotency
Проще всего через docker exec:
```bash
docker exec -it safebox-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic payment-events
```
Вставь (одной строкой):
```
{"eventId":"evt-1","applicationId":"<ID>","amount":3000,"occurredAt":"2026-05-07T10:00:00Z"}
```
В логах сервиса увидишь обработку, в БД статус заявки → `PAID`.

**Теперь повторно вставь то же сообщение** (тот же `eventId`). В логах:
```
Duplicate event evt-1, skip
```
Это и есть idempotency через `processed_events` с unique constraint.

### 4.5. Проверь processed_events
```bash
docker exec -it safebox-postgres psql -U safebox -d safebox -c "select * from processed_events;"
```

---

## 5. Пощупать Redis + cache-aside

### 5.1. Первый запрос (cache miss)
```bash
curl -s -X POST http://localhost:8081/api/v1/pricing/calculate \
  -H 'Content-Type: application/json' \
  -d '{"cellType":"MEDIUM","days":30,"segment":"VIP"}'
```

### 5.2. Что в Redis
```bash
docker exec -it safebox-redis redis-cli KEYS 'price:*'
docker exec -it safebox-redis redis-cli TTL price:MEDIUM:30:VIP
docker exec -it safebox-redis redis-cli GET price:MEDIUM:30:VIP
```

### 5.3. Второй запрос (cache hit)
Повтори 5.1. Проверь метрики:
```bash
curl -s http://localhost:8081/actuator/prometheus | grep pricing_cache
```
Увидишь `pricing_cache_hit_total` и `pricing_cache_miss_total`.

### 5.4. Положи Redis (fallback)
```bash
docker compose stop redis
curl -s -X POST http://localhost:8081/api/v1/pricing/calculate \
  -H 'Content-Type: application/json' \
  -d '{"cellType":"LARGE","days":7,"segment":"STANDARD"}'
docker compose start redis
```
Сервис должен ответить нормально (fallback на compute). В логах: `Redis unavailable, fallback to compute`.

---

## 6. Мониторинг + логи

### 6.1. Бизнес-метрики
```bash
curl -s http://localhost:8081/actuator/prometheus | grep -E '^applications_|^payments_'
```
Видишь `applications_created_count_total`, `applications_confirmed_count_total`, `payments_completed_count_total`.

### 6.2. Технические метрики
```bash
curl -s http://localhost:8081/actuator/prometheus | grep -E 'http_server_requests|jvm_memory|kafka_consumer'
```

### 6.3. Prometheus UI
http://localhost:9090 → Status → Targets — должен быть `UP` для `application-service`.

Графики (вставь в поле query):
- `rate(http_server_requests_seconds_count[1m])` — RPS
- `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))` — p95 latency
- `applications_created_count_total` — счётчик заявок

### 6.4. Grafana
http://localhost:3000 (admin/admin) → Connections → Data sources → Add → Prometheus → URL `http://prometheus:9090` → Save & test.
Затем Dashboards → New → Add visualization → выбери метрику.

### 6.5. traceId в логах
```bash
curl -i -H 'X-Trace-Id: my-trace-1' http://localhost:8081/api/v1/applications
```
В логе сервиса: `INFO [traceId=my-trace-1] ...`. Это связывает запрос со всеми его логами.

---

## 7. Что говорить на собесе про каждый кусок

**JPA + @Version**
> «Использую optimistic locking через `@Version`. Hibernate добавляет `WHERE version = ?` в update, при конфликте кидает `OptimisticLockException`. Подходит, когда конфликты редкие. Для бронирования ячейки переходил на pessimistic — `SELECT ... FOR UPDATE`».

**Liquibase**
> «Каждая миграция в отдельном changeset, имеет id и author. Liquibase ведёт таблицу `databasechangelog`, не накатывает повторно. Можно делать rollback по changeset. Сравнивал с Flyway — Liquibase гибче (yaml/xml/json), Flyway проще (только SQL)».

**Outbox**
> «Не бывает атомарной транзакции БД + Kafka. Если БД закомитилась, а Kafka упала — потеряли событие. Поэтому в одной транзакции пишу бизнес-данные и запись в outbox. Отдельный scheduler читает outbox и публикует в Kafka. После успешной отправки помечаю SENT. При ошибке — retry_count, после 5 попыток FAILED».

**Idempotency**
> «Kafka это at-least-once, дубль возможен. Решал через таблицу `processed_events` с PK по event_id. В одной транзакции делаю insert и бизнес-операцию. Если insert упал на unique violation — дубль, бизнес-логику не выполняем, просто ack».

**Kafka consumer settings**
> «`enable.auto.commit=false` плюс manual ack — управляю сам, чтобы не закомитить offset до того как обработал. `max.poll.records=50` — батч. `max.poll.interval.ms=5min` — если не вызову poll за это время, выкинут из группы и начнётся rebalance».

**Partition key**
> «Использую `applicationId` как key. Все события по одной заявке попадают в одну партицию и обрабатываются по порядку. Это важно: создание → подтверждение → оплата нельзя обрабатывать out-of-order».

**Redis cache-aside**
> «При запросе цены сначала иду в Redis. Cache hit — возвращаю. Cache miss — считаю, кладу в Redis с TTL 30 минут. Если Redis упал — fallback на расчёт без кэша, чтобы не лежать. Метрики hit/miss в Prometheus, видно cache hit ratio».

**Метрики бизнес vs технические**
> «Технические метрики говорят, что система работает (RPS, p95, error rate, JVM). Бизнесовые — что она работает правильно (заявок создано, оплат прошло). Latency может быть отличный, а заявок 0 в час — значит сломано выше по стеку».

**traceId**
> «Через filter беру `X-Trace-Id` из header или генерю UUID. Кладу в MDC, в логах через паттерн `%X{traceId}`. На выход отдаю тот же header. В микросервисах это даёт сквозную трассировку запроса через все сервисы».

---

## 8. Kubernetes и GitLab CI

См [docs/k8s.md](docs/k8s.md) и [docs/gitlab-ci.md](docs/gitlab-ci.md). Запускать локально не нужно — это **образцы для чтения и обсуждения на собесе**.

## 9. Code review

См [docs/code-review.md](docs/code-review.md). Чек-лист и как описывать процесс на собесе.
