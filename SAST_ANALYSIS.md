# Отчёт по практическому заданию №6

Дата: 27.03.2026  
Проект: `course-management`  
Ветка: `practice-6`

## 1. Что нужно было сделать

1. Найти уязвимости в проекте.
2. Обновить отчёт из ПЗ-5.
3. Предложить исправления.
4. Исправить реальные уязвимости.
5. Подготовить изменения для PR/MR.

## 2. Реальные уязвимости

1. SQL-инъекция в поиске курсов.
2. Небезопасный разбор XML (XXE-риск).
3. SSRF: запросы по произвольному URL.
4. Загрузка и запуск кода из внешнего URL.
5. Небезопасная аутентификация:
   - пароль в открытом виде;
   - логирование пароля;
   - возможность выбрать роль при регистрации.
6. Слабые настройки безопасности:
   - CORS `*`;
   - открытые management endpoint’ы;
   - вывод stacktrace в ответах.
7. Слабый container hardening:
   - запуск от root;
   - docker socket в `docker-compose`.

## 3. Ложные срабатывания

1. Часть Semgrep-правил для Django CSRF (проект на Spring, не Django).
2. Часть DAST-предупреждений по кэшированию/редиректам (шум hardening-уровня).

## 4. Что исправили

1. Сделали безопасный SQL-запрос в поиске.
2. Переписали XML-парсер на безопасный.
3. Удалили SSRF-endpoint’ы.
4. Удалили динамическую загрузку внешнего кода.
5. Подключили Spring Security.
6. Пароли перевели на BCrypt.
7. Запретили выбор роли при регистрации.
8. Добавили CSRF-токены в HTML-формы.
9. Ужесточили `application.yaml`.
10. Ужесточили `Dockerfile` и `docker-compose.yml`.
11. Исправили DAST в GitHub Actions (прямой запуск ZAP через Docker).

## 5. Код из мест, где были уязвимости

### 5.1 SQL-инъекция (было / стало)

Было:
```java
public List<Course> searchByTitle(String title) {
    String sql = "SELECT id, title, description, teacher_id FROM courses WHERE title = '" + title + "'";
    return jdbc.query(sql, rm);
}
```

Стало:
```java
public List<Course> searchByTitle(String title) {
    String sql = "SELECT id, title, description, teacher_id FROM courses WHERE title = ?";
    return jdbc.query(sql, rm, title);
}
```

### 5.2 Небезопасный XML (XXE) (было / стало)

Было:
```java
SAXReader reader = new SAXReader();
Document doc = reader.read(new StringReader(xml));
return doc.getRootElement().getText();
```

Стало:
```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
```

### 5.3 SSRF: proxy endpoint (было)

Было:
```java
@GetMapping("/api/proxy")
public String proxy(@RequestParam("targetUrl") String targetUrl) {
    RestTemplate rt = new RestTemplate();
    return rt.getForObject(targetUrl, String.class);
}
```

Исправление: `ProxyController` удалён.

### 5.4 SSRF: импорт по URL (было)

Было:
```java
@GetMapping("/api/courses/import")
@ResponseBody
public String importFromUrl(@RequestParam String url) {
    RestTemplate rt = new RestTemplate();
    String json = rt.getForObject(url, String.class);
    log.info("Импортированы данные курсов (raw): {}", json);
    return "OK";
}
```

Исправление: endpoint удалён из `CourseController`.

### 5.5 Загрузка внешнего кода (было)

Было:
```java
URL url = new URL(pluginUrl);
try (URLClassLoader cl = new URLClassLoader(new URL[]{url}, this.getClass().getClassLoader())) {
    Class<?> clazz = Class.forName("com.example.PluginMain", true, cl);
    Method m = clazz.getDeclaredMethod("init");
    m.invoke(null);
}
```

Исправление: `PluginLoader` удалён.

### 5.6 Небезопасный логин и регистрация (было / стало)

Было:
```java
if (u.getPassword().equals(password)) {
    log.info("User {} logged in with password {}", username, password);
}

users.save(new User(null, username, password, role));
```

Стало:
```java
users.register(username, password); // пароль хэшируется, роль фиксированная STUDENT
```

И в сервисе:
```java
public User register(String username, String rawPassword) {
    User user = new User(null, username, passwordEncoder.encode(rawPassword), "STUDENT");
    return repo.save(user);
}
```

### 5.7 Открытый CORS (было)

Было:
```java
registry.addMapping("/**")
        .allowedMethods("*")
        .allowedOrigins("*")
        .allowedHeaders("*");
```

Исправление: `WebConfig` удалён, контроль доступа выполняется через Spring Security.

### 5.8 Слабый `application.yaml` (было / стало)

Было:
```yaml
h2:
  console:
    enabled: true
    settings:
      web-allow-others: true
management:
  endpoints:
    web:
      exposure:
        include: "*"
server:
  error:
    include-stacktrace: ALWAYS
```

Стало:
```yaml
h2:
  console:
    enabled: false
management:
  endpoints:
    web:
      exposure:
        include: "health,info"
server:
  error:
    include-stacktrace: never
```

### 5.9 Docker hardening (было / стало)

Было (`Dockerfile`):
```dockerfile
FROM eclipse-temurin:21-jdk
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Стало (`Dockerfile`):
```dockerfile
FROM eclipse-temurin:21-jre
RUN addgroup --system app && adduser --system --ingroup app app
USER app
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Было (`docker-compose.yml`):
```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
  - ./gitlab-runner-config:/etc/gitlab-runner
```

Стало (`docker-compose.yml`):
```yaml
read_only: true
security_opt:
  - no-new-privileges:true
tmpfs:
  - /tmp
volumes:
  - ./gitlab-runner-config:/etc/gitlab-runner
```

## 6. Проверка

Локально выполнено:
```bash
./mvnw -B test
```

Результат: тесты проходят.

## 7. Итог

1. Реальные уязвимости найдены и исправлены.
2. Ложные срабатывания отделены от реальных проблем.
3. В отчёт добавлены конкретные фрагменты кода из уязвимых мест.
4. Проект готов к PR/MR по ПЗ-6.
