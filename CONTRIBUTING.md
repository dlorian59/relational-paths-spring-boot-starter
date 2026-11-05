# Development / Разработка

[README](README.md) · [Integration EN](docs/0.1.0/integration.en.md) · [Интеграция RU](docs/0.1.0/integration.ru.md)

Use a full JDK 17+ and sibling core/starter checkouts. The starter resolves core through a normal artifact dependency. / Нужен полный JDK 17+ и соседние рабочие копии core/стартера. Стартер разрешает core через обычную зависимость на артефакт.

```bash
cd ../relational-paths
./gradlew publishToMavenLocal
cd ../relational-paths-spring-boot-starter
./gradlew build example publishToMavenLocal
```

Local preparation does not publish to Maven Central. Gradle consumers need mavenLocal() in their repositories for these local artifacts; Maven checks its local repository automatically. / Локальная подготовка не публикует в Maven Central. Gradle consumer нужны repositories с mavenLocal(); Maven проверяет локальный репозиторий автоматически.

## Independent consumer / Отдельное приложение

The runnable business application is in examples/order-report. Its Maven tests create only a private in-memory H2 database and apply no production migration. / Запускаемое бизнес-приложение находится в examples/order-report. Maven-тесты создают только приватную H2 в памяти и не применяют рабочие миграции.

```bash
cd examples/order-report
mvn package
```

Expected / Ожидается:

```text
CONSUMER_PASS: shipping route, parameter isolation, tenant isolation, missing context rejected
```

The test starts a real Boot context from the artifact's auto-configuration, uses JDBC to populate tenant-isolated data, dispatches controller requests through Spring MVC, and checks actual rows. H2 is test-only. Deploying against PostgreSQL requires your existing schema, REPORT_DB_URL/USER/PASSWORD and authenticatedTenantId supplied by your verified authentication layer. / Тест запускает настоящий контекст Boot через автоконфигурацию артефакта, заполняет данные арендаторов через JDBC, направляет запросы контроллеру через Spring MVC и проверяет строки. H2 используется только в тестах. Для PostgreSQL нужны ваша схема, REPORT_DB_URL/USER/PASSWORD и authenticatedTenantId от проверенного слоя авторизации.

For a deployment-like local launch after supplying those application prerequisites / Локальный запуск после подготовки этих условий приложения:

```bash
java -jar target/order-report-1.0.0.jar
```

Unauthenticated calls lack the server attribute and are rejected. No sample header bypass or built-in login is provided. / Без авторизации серверного атрибута нет и запрос отклоняется. Обхода через sample header и встроенного входа нет.

## OSS-013 verification / Проверка

On Java 17, core and starter builds, Javadoc, regression tests and local publication passed. A fresh copied Maven consumer resolved the actual artifacts and passed the H2/JDBC/MVC scenario above. The standalone core service also compiled and ran against the installed JAR. No external registry release, PostgreSQL runtime execution or network-disconnect behavior is claimed for this new sample. / На Java 17 прошли сборки core/стартера, Javadoc, регрессии и локальная установка. Отдельная копия Maven consumer разрешила реальные артефакты и прошла сценарий H2/JDBC/MVC. Отдельный core-сервис также скомпилирован и запущен с установленным JAR. Для нового примера не заявляются внешний релиз, выполнение PostgreSQL и поведение при сетевом разрыве.

Use -PtestJavaVersion with another installed JDK for a targeted check. Compilation always targets Java 17; supported LTS 17/21/25 and optional 27 do not enable separate library features. / Для отдельной проверки другого установленного JDK используйте -PtestJavaVersion. Компиляция всегда нацелена на Java 17; LTS 17/21/25 и optional 27 не включают отдельных возможностей API.
