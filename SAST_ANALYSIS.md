# Отчет по анализу безопасности (SAST + SCA)

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

1. Правило `python.django.security.django-no-csrf-token.django-no-csrf-token` на HTML-шаблонах проекта.  
   Причина: стек проекта Spring Boot + Thymeleaf, а не Django.

## 2) SCA (OWASP Dependency-Check)

Команда запуска:

```bash
./mvnw -B -DskipTests dependency-check:check
```

Отчет: `target/dependency-check-report.html`.

### Найденные уязвимости до исключений

- Всего: 9
- Затронутые артефакты:
  - `dom4j:dom4j:1.6.1` -> `CVE-2020-10683`
  - `org.apache.tomcat.embed:tomcat-embed-core:10.1.43` -> `CVE-2025-48989`, `CVE-2025-55752`, `CVE-2025-55754`, `CVE-2025-61795`, `CVE-2025-66614`, `CVE-2026-24733`, `CVE-2026-24734`
  - `org.apache.logging.log4j:log4j-api:2.24.3` -> `CVE-2025-68161`

### Реальные уязвимости (SCA)

1. `CVE-2020-10683` (`dom4j:1.6.1`)  
   Реальная: библиотека устарела и уязвима к XXE при небезопасной конфигурации XML-парсинга.

2. CVE в `tomcat-embed-core:10.1.43`  
   Реальные: версия попадает в уязвимые диапазоны из отчета.

### Ложные срабатывания (SCA)

1. `CVE-2025-68161` на `log4j-api`  
   Ложное/неприменимое срабатывание для данного проекта: CVE относится к `log4j-core` (Socket Appender), а в проекте `log4j-core` не используется.

Добавлено подавление в `dependency-check-suppressions.xml` по `packageUrl` `log4j-api` + `CVE-2025-68161`.

## 3) Варианты устранения

1. `dom4j`:
   - обновить `dom4j` минимум до безопасной ветки (`2.1.3+`, предпочтительно актуальную стабильную);
   - дополнительно включить безопасную конфигурацию XML-парсера (запрет внешних сущностей/DTD).

2. `tomcat-embed-core`:
   - обновить Spring Boot до версии, где подтягивается исправленный Tomcat (`10.1.52+`);
   - как временный вариант: зафиксировать `tomcat.version` в `pom.xml` на патч-версию с исправлениями.

3. Для уменьшения шума отчетов:
   - поддерживать файл подавлений `dependency-check-suppressions.xml` только для подтвержденных false positive;
   - регулярно пересматривать подавления при обновлении зависимостей.

## 4) Ускорение загрузки CVE в CI/CD

Реализовано:

1. Выделенный `dataDirectory` для Dependency-Check: `.dependency-check-data`.
2. Кэширование этой директории в GitLab CI и GitHub Actions.
3. Логика fast-path:
   - если база CVE уже есть в кэше, запуск `dependency-check:check` выполняется с `-DautoUpdate=false`;
   - если кэша нет, выполняется `dependency-check:update-only` и затем проверка.
4. Поддержка `NVD_API_KEY` через переменную окружения (ускоряет первичную синхронизацию NVD).
