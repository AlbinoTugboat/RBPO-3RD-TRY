# Отчёт по анализу SAST (Semgrep CE)

Дата сканирования: 27.03.2026  
Инструмент: Semgrep Community Edition  
Профиль правил: `p/default`

Команда сканирования:

```bash
semgrep scan --config p/default --metrics=off .
```

## Реальные уязвимости

1. `src/main/java/ru/mtuci/coursemanagement/service/CourseService.java:42`  
Правило: `java.spring.security.audit.spring-sqli.spring-sqli`  
Проблема: SQL-запрос собирается конкатенацией пользовательского `title`, что допускает SQL Injection.  
Устранение: перейти на параметризованный запрос (`?`) через `JdbcTemplate` с параметрами.

2. `Dockerfile:5`  
Правило: `dockerfile.security.missing-user-entrypoint.missing-user-entrypoint`  
Проблема: контейнер запускается от `root`.  
Устранение: добавить создание отдельного пользователя и `USER app` перед `ENTRYPOINT`.

3. `docker-compose.yml:7-9`  
Правило: `yaml.docker-compose.security.exposing-docker-socket-volume.exposing-docker-socket-volume`  
Проблема: проброс `/var/run/docker.sock` в контейнер даёт root-эквивалент на хосте.  
Устранение: убрать прямой проброс docker socket, перейти на изолированный executor/remote Docker API с ограничениями.

4. `docker-compose.yml:3`  
Правила:  
`yaml.docker-compose.security.writable-filesystem-service.writable-filesystem-service`,  
`yaml.docker-compose.security.no-new-privileges.no-new-privileges`  
Проблема: отсутствуют `read_only: true` и `security_opt: [no-new-privileges:true]`.  
Устранение: включить эти ограничения, а запись направить в `tmpfs`/volume только для нужных каталогов.

## Ложные срабатывания

1. `src/main/resources/templates/courses.html:17-22`  
Правило: `python.django.security.django-no-csrf-token.django-no-csrf-token`

2. `src/main/resources/templates/login.html:6-10`  
Правило: `python.django.security.django-no-csrf-token.django-no-csrf-token`

3. `src/main/resources/templates/login.html:14-19`  
Правило: `python.django.security.django-no-csrf-token.django-no-csrf-token`

4. `src/main/resources/templates/students.html:17-22`  
Правило: `python.django.security.django-no-csrf-token.django-no-csrf-token`

Обоснование: проект использует Spring Boot + Thymeleaf, а не Django; правило неприменимо к данному стеку.

## Предложение по снижению шума отчёта

1. Исключить правило Django из профиля Semgrep или подключать профиль для Java/Spring отдельно.
2. В CI оставить публикацию полного отчёта, но для quality gate учитывать только применимые к стеку правила.
