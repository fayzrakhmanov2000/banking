# GitLab CI/CD — образец и что говорить на собесе

Файл: [`.gitlab-ci.yml`](../.gitlab-ci.yml).

## Стадии и что они делают

| Stage | Что | Почему |
|---|---|---|
| `build` | компиляция, сборка jar | быстро падать на синтаксисе |
| `test` | unit + integration через Testcontainers | на ранней стадии |
| `quality` | SonarQube, code coverage, линт | блокировать MR при падении gate |
| `package` | docker build + push в registry | один артефакт на всю жизнь pipeline |
| `deploy` | `kubectl set image` + `rollout status` | автодеплой на staging, prod через manual |

## Ключевые приёмы и зачем

**Cache `.gradle/`** — без кэша каждый job скачивает зависимости заново, минуты в трубу.

**Docker-in-Docker (`dind`)** — чтобы билдить образ внутри CI runner-а, который сам в контейнере.

**Один image на pipeline (`$CI_COMMIT_SHORT_SHA`)** — ровно тот же артефакт, который прошёл тесты, едет в staging и потом в prod. Никаких пересборок «уже на проде что-то другое».

**`when: manual` на prod** — кнопка в GitLab, человек жмёт. Защита от автоматического выкатывания в боевую среду.

**Secrets через `$CI_REGISTRY_PASSWORD`, `$KUBECONFIG_PROD`** — хранятся в GitLab CI/CD Variables (masked, protected). Никогда не коммитим в репо.

**`only: main`** — деплой только из мастера. Из feature-веток крутятся build/test, но не package/deploy.

**`environment: production`** — в GitLab UI появляется страница environments, история деплоев, кнопка rollback на предыдущий деплой.

## Что говорить на собесе

> «У нас pipeline на 5 стадий: build, test, quality, package, deploy. На MR крутятся первые три плюс SonarQube. После merge в master — собирается Docker-образ с тегом `commit_sha`, пушится в private registry, автоматически выкатывается на staging через `kubectl rollout`. На prod — manual approval, нажимает тимлид. Откат — `kubectl rollout undo` или повторный деплой с предыдущим тегом».

## Частые вопросы

**Как тестируешь миграции?**
> «В job `test` поднимаем Postgres через `services:` (или через Testcontainers внутри тестов). Liquibase накатывается при старте Spring контекста, если упадёт — упадёт build».

**Что если миграция несовместима с предыдущей версией кода?**
> «Делаем backward-compatible миграции в две фазы. Сначала **expand** — добавляем колонку nullable, новый код пишет в неё, старый игнорит. Потом релиз. Потом **contract** — делаем not null или удаляем старую колонку. Это даёт zero-downtime deploy».

**Blue-green vs canary vs rolling?**
> «У нас rolling update в K8s. Blue-green = две полные среды, переключаешь трафик. Canary = выкатываем на 5% подов и смотрим метрики. У нас был rolling плюс readiness probe».

**Как мониторишь что деплой прошёл?**
> «`kubectl rollout status` блокирует pipeline пока все поды не readiness=true. Плюс smoke-test после деплоя — curl на `/actuator/health` нового пода. Плюс в Grafana алерт на error rate > 1%, если после деплоя растёт — alert и rollback».
