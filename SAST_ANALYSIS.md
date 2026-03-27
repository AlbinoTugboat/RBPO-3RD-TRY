# Отчет по анализу безопасности (SAST + SCA + DAST)

Дата обновления: 27.03.2026  
Проект: `course-management`

## 1) SAST (Semgrep CE)

Команда запуска:

```bash
semgrep scan --config p/default --metrics=off .
```

Для GitLab используется локальный профиль `.semgrep/gitlab-rules.yml` (офлайн-режим).

### Реальные уязвимости (SAST)

1. `src/main/java/ru/mtuci/coursemanagement/service/CourseService.java:33`  
   SQL Injection: SQL-запрос собирается конкатенацией пользовательского `title`.
2. `Dockerfile:5`  
   Контейнер запускается от `root`.
3. `docker-compose.yml:7-9`  
   Проброс Docker socket (`/var/run/docker.sock`) в контейнер.
4. `docker-compose.yml:3`  
   Нет ограничений `read_only` и `no-new-privileges`.

### Ложные срабатывания (SAST)

1. `python.django.security.django-no-csrf-token.django-no-csrf-token` на HTML-шаблонах проекта.  
   Причина: проект использует Spring Boot + Thymeleaf, а не Django.

## 2) SCA (OWASP Dependency-Check)

Команда запуска:

```bash
./mvnw -B -DskipTests dependency-check:check
```

Отчет: `target/dependency-check-report.html`.

### Найденные уязвимости до исключений

1. `dom4j:dom4j:1.6.1` -> `CVE-2020-10683`
2. `org.apache.tomcat.embed:tomcat-embed-core:10.1.43` -> `CVE-2025-48989`, `CVE-2025-55752`, `CVE-2025-55754`, `CVE-2025-61795`, `CVE-2025-66614`, `CVE-2026-24733`, `CVE-2026-24734`
3. `org.apache.logging.log4j:log4j-api:2.24.3` -> `CVE-2025-68161`

### Реальные уязвимости (SCA)

1. `CVE-2020-10683` (`dom4j:1.6.1`)  
   Устаревшая библиотека + риск XXE при небезопасной конфигурации XML-парсинга.
2. CVE в `tomcat-embed-core:10.1.43`  
   Версия попадает в уязвимые диапазоны из отчета.

### Ложные срабатывания (SCA)

1. `CVE-2025-68161` на `log4j-api`  
   Неприменимо к проекту: CVE относится к `log4j-core` (Socket Appender), которого нет в используемом стеке.

Подавление добавлено в `dependency-check-suppressions.xml`.

## 3) DAST (OWASP ZAP)

Локальные команды для валидации:

```bash
docker run --rm -v "${PWD}:/zap/wrk/:rw" -w /zap/wrk zaproxy/zap-stable zap-baseline.py -t http://host.docker.internal:8080 -c .zap/rules.tsv -r target/zap-baseline-report.html -J target/zap-baseline-report.json -w target/zap-baseline-report.md -I
docker run --rm -v "${PWD}:/zap/wrk/:rw" -w /zap/wrk zaproxy/zap-stable zap-api-scan.py -f openapi -t .zap/openapi-docker.yaml -c .zap/rules.tsv -r target/zap-api-report.html -J target/zap-api-report.json -w target/zap-api-report.md -I
```

### Реальные уязвимости/риски (DAST)

1. Отсутствуют базовые security headers на веб-страницах/части API:  
   `Missing Anti-clickjacking Header`, `X-Content-Type-Options Header Missing`, `CSP Header Not Set`, `Permissions-Policy Header Not Set`.
2. Отсутствует `SameSite` у session cookie (`Cookie without SameSite Attribute`).
3. Отсутствуют anti-CSRF токены в формах (`Absence of Anti-CSRF Tokens`).
4. Возможна фиксация/утечка session id через URL (`Session ID in URL Rewrite`).
5. Есть раскрытие деталей ошибок на `POST /register` (`Application Error Disclosure` / `Information Disclosure - Debug Error Messages`).

### Ложные срабатывания/шум (DAST)

1. `Unexpected Content-Type was returned` в API-скане по корневым URL.  
   Причина: для UI-эндпоинтов возвращается HTML, это ожидаемо и не является уязвимостью.
2. Часть disclosure-срабатываний на `POST /register` спровоцирована тестовым запросом без обязательных параметров и относится к hardening-шуму, а не к прямому эксплуатационному сценарию.

## 4) Варианты устранения

1. Подключить Spring Security и задать защитные заголовки (`X-Frame-Options`, `X-Content-Type-Options`, CSP, Permissions-Policy).
2. Включить CSRF-защиту для state-changing форм (`POST/PUT/DELETE`) и добавить CSRF token в Thymeleaf-формы.
3. Настроить cookie-политику: `HttpOnly`, `Secure`, `SameSite=Lax/Strict`.
4. Запретить URL rewriting для сессий и использовать только cookie-механизм сессии.
5. Скрыть диагностические детали в error-ответах, добавить единый `@ControllerAdvice`/кастомный error handler без утечки внутренних сообщений.
6. Для SCA: обновить `dom4j` и `tomcat-embed-core` до исправленных версий, поддерживать suppressions только для подтвержденных false positive.

## 5) Изменения CI для ПЗ-5

1. GitLab CI: добавлены job `dast_baseline` и `dast_api` на этапе `test` с `zaproxy/zap-stable`, запуском jar в фоне, ожиданием старта и выгрузкой ZAP-отчетов в artifacts.
2. GitHub Actions: добавлены job `dast_baseline` и `dast_api` с `actions/download-artifact@v4`, запуском jar в фоне, `zaproxy/action-baseline@v0.9.0`, `zaproxy/action-api-scan@v0.10.0` и выгрузкой отчетов через `actions/upload-artifact@v4`.
