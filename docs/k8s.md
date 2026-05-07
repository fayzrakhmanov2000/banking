# Kubernetes — образец и что говорить на собесе

Манифест: [`k8s/application-service-deployment.yaml`](../k8s/application-service-deployment.yaml).

## Что в манифесте и зачем

### Deployment
- `replicas: 3` — три пода для отказоустойчивости. Если один умрёт, трафик пойдёт на оставшиеся.
- `RollingUpdate` с `maxUnavailable: 0`, `maxSurge: 1` — выкатываем по одному поду, без даунтайма.
- `image: registry.example.com/...:1.0.0` — **тегаем версией**, никогда `latest` (иначе непредсказуемо что выкатилось).

### Probes (важно для собеса)
- **livenessProbe** — «жив ли процесс». Если падает, K8s рестартит под. Нельзя ставить туда тяжёлые проверки (БД, Kafka), иначе при недоступности зависимостей K8s начнёт рестартить поды в петлю.
- **readinessProbe** — «готов ли принимать трафик». Если не ОК, K8s **не убивает** под, но снимает его из Service. Сюда можно класть зависимости (БД, Kafka). Spring Boot Actuator даёт `/actuator/health/readiness` и `/actuator/health/liveness` отдельно.
- `initialDelaySeconds` — даём приложению стартануть, чтобы probe не начал стучать сразу.

### Resources
- `requests` — гарантированный минимум (по нему K8s планирует поды на ноды).
- `limits` — потолок. Превышение CPU → throttling. Превышение memory → **OOMKilled**, под перезапускается.
- На собесе спросят: «как выбираешь limits»? Ответ: профилирование под нагрузкой плюс запас. Heap для Java задаём через `-Xmx` чуть ниже memory limit, чтобы оставить место на metaspace и off-heap.

### ConfigMap и Secret
- `ConfigMap` — несекретная конфигурация (URL БД, Kafka bootstrap).
- `Secret` — пароли, токены. Хранятся base64 (не шифрование), для шифрования — sealed-secrets, vault, KMS.
- На собесе скажи: «секреты в репо мы не храним. Используем sealed-secrets или Vault, в pipeline они декриптуются перед apply».

### HorizontalPodAutoscaler (HPA)
- Скейлит по CPU 70%. Можно скейлить по custom metrics (Kafka lag, RPS).
- На собесе: «у нас был сервис с пиковой нагрузкой утром, поставили HPA по CPU, в пик дорастал до 8 подов, ночью схлопывался до 3. Экономия ресурсов».

### Service
- `ClusterIP` (по умолчанию) — внутри кластера. Снаружи — через `Ingress` или `LoadBalancer`.

## Что ещё спросят

**`kubectl` команды, которые ты «использовал»:**
```
kubectl get pods -n safebox
kubectl logs -f deployment/application-service
kubectl describe pod <pod>            # смотреть события (OOMKilled, ImagePullError)
kubectl rollout status deployment/application-service
kubectl rollout undo deployment/application-service   # откат
kubectl exec -it <pod> -- sh
kubectl top pods                       # CPU/mem
kubectl port-forward svc/application-service 8081:80
```

**Headless service** — service без `ClusterIP`, DNS возвращает IP всех подов. Нужен для StatefulSet (Kafka, Postgres).

**StatefulSet vs Deployment** — для stateful (БД, Kafka). Стабильные имена подов (`kafka-0`, `kafka-1`), стабильные PVC, упорядоченный rollout.

**Namespace** — изоляция по командам/средам (dev, staging, prod).

**Network Policy** — кто может ходить к кому. По умолчанию все могут ко всем — это плохо для прода.

## Если попросят показать «как ты деплоил»

> «Build → Docker image → push в private registry → в pipeline `kubectl apply -f k8s/` или Helm chart. Образ тегается коммитом или версией. Деплой через RollingUpdate, маленькими порциями. После rollout — smoke-тесты на новой версии. Если падают — `kubectl rollout undo`».

## Helm vs raw manifests

> «Для одного сервиса хватает raw yaml. Когда сервисов много — Helm chart, шаблонизируем разницу между средами через `values-dev.yaml`, `values-prod.yaml`. Альтернативы — Kustomize, ArgoCD».
