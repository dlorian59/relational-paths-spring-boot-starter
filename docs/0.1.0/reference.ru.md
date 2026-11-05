# relational-paths-spring-boot-starter 0.1.0 — полный справочник

[← Руководство](ru.md) · [Версии](../index.md) · [README](../../README.md) · [English](reference.en.md)

Стартер интегрирует `relational-paths:0.3.0` со Spring Boot 4.1.2. Он настраивает обычные неизменяемые объекты планирования, а не БД, ORM или AOP-перехватчик. Начните с [полного сценария отчёта](integration.ru.md#начните-с-бизнес-задачи).

## Публичные типы и точки интеграции

| API | Назначение и применение | Граница |
| --- | --- | --- |
| [RelationalPathsAutoConfiguration](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfiguration.java) | Обнаруживается через [AutoConfiguration.imports](../../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports); достаточно штатного запуска Boot. | Не создавайте и не component-scan этот класс вручную. Фабричные методы beans внутренние; переопределяйте объявлениями core-типов. |
| [RelationalPathsProperties](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java) | Неизменяемый record с `maxTables()`, `maxRelationships()`, `maxEdgeVisits()` и стандартными accessors/equality. | Boot binding задаёт defaults отсутствующих свойств; прямой `new RelationalPathsProperties(...)` требует три int и сам не проверяет core-лимиты. |
| [toSearchLimits()](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java) | Создаёт core `SearchLimits` и проверяет числовые границы. | Схема проверяется только при создании планировщика. Метод не обеспечивает динамическое обновление настроек. |
| Bean `Schema` приложения | [ReportConfiguration](../../src/examples/java/example/reports/ReportConfiguration.java) объявляет разрешённые роли, колонки и связи. | Метаданные, tenant-политика и доступ принадлежат приложению; сканирования и автоматической авторизации нет. |
| Внедрённый `JoinPlanner` / собственный `SearchLimits` | Внедрение в конструктор [OrderReport](../../src/examples/java/example/reports/OrderReport.java); матрица активации ниже. | Core API не расширяется; `SqlRenderer.select` остаётся static. |

## Properties и defaults

| Свойство | По умолчанию | Контракт |
| --- | --- | --- |
| `relational-paths.enabled` | `true` | Условие автоконфигурации; используйте true/false. Не является компонентом RelationalPathsProperties. Отсутствие означает включение, но кандидат Schema всё равно обязателен. |
| `relational-paths.max-tables` | `512` | Java int ≥ 1 при создании действующего SearchLimits; учитываются все роли схемы. Превышение размера отклоняет конструктор JoinPlanner. |
| `relational-paths.max-relationships` | `8192` | Java int ≥ 0; учитываются все объявленные связи. Ноль допускает схему без рёбер. |
| `relational-paths.max-edge-visits` | `8192` | Java int ≥ 0; просмотренные исходящие рёбра одного автоматического поиска. Ноль допускает только корень или полностью явный валидный план, но не просмотр ребра. |

Используйте application.properties/YAML или стандартную внешнюю конфигурацию Boot. Настройки с парными комментариями находятся в [application.properties](../../src/examples/resources/application.properties). Стандартный binding игнорирует неизвестные свойства: при опечатке нужная настройка останется default; сверяйте написание с [исходником дополнительных метаданных](../../src/main/resources/META-INF/additional-spring-configuration-metadata.json) и [классом Properties](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java). Core-лимиты проверяются при переводе в действующий SearchLimits. Собственный bean не означает отключения всего binding свойств.

```properties
# These are hard metadata traversal bounds, not SQL row or query-time limits.
# Это жёсткие границы обхода метаданных, а не число строк или время SQL.
relational-paths.enabled=true
relational-paths.max-tables=32
relational-paths.max-relationships=64
relational-paths.max-edge-visits=64
```

## Активация и приоритет beans

Таблица описывает создание планировщика/лимитов; собственные beans остаются в контексте. Фасад дополнительно создаётся только при одном разрешимом JoinPlanner и отсутствии пользовательского RelationalQueries. Несколько планировщиков без основного не приводят к произвольному выбору или ошибке создания фасада. Условия и приоритет проверяют [существующие контекстные тесты](../../src/test/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfigurationTest.java).

| Состояние приложения | Создаваемые beans | Следствие |
| --- | --- | --- |
| Нет Schema / выключено / отсутствует core-класс | Ничего | Приложение всё равно должно обеспечить собственные места внедрения JoinPlanner. |
| Одна Schema; нет планировщика и лимитов | SearchLimits из properties + JoinPlanner | По умолчанию: 512 / 8192 / 8192. |
| Несколько Schema; ровно одна @Primary | Как для одной выбранной Schema | Выбор основной схемы — обычное внедрение Spring. |
| Несколько Schema; нет одного кандидата | Ничего | Нет произвольного выбора. Добавьте @Primary или явно создайте планировщики приложения. |
| Выбранная Schema + собственный SearchLimits, нет планировщика | JoinPlanner с этим bean лимитов | Default SearchLimits не создаётся; properties не перезаписывают объект. Нескольким beans лимитов нужен основной/уникальный кандидат. |
| Выбранная Schema + собственный JoinPlanner | Ни default-планировщика, ни default-лимитов | Конструирование, схема и лимиты собственного планировщика — ответственность приложения. Существующие лимиты не меняются. |

При активной автоконфигурации неизменяемый bean properties регистрируется и при пользовательском планировщике. Имена default-beans — `relationalPathsSearchLimits`, `relationalPathsJoinPlanner` и `relationalQueries`; замена определяется типом, а не именем. Для собственных лимитов достаточно `@Bean` в обычной конфигурации приложения:

```java
// A custom limits bean replaces property-derived defaults for the auto-configured planner.
// Собственный bean лимитов заменяет defaults из properties у автоматического планировщика.
@Bean
SearchLimits reportLimits() {
    return new SearchLimits(32, 64, 64);
}
```

Метод размещается в `@Configuration` с импортами `Bean` и core `SearchLimits`. Для нескольких планировщиков создайте именованные `JoinPlanner` с нужными схемами/лимитами и внедряйте через `@Qualifier`; стартер отступает при наличии любого bean планировщика. Реестра и автоматического планировщика для каждой схемы нет.

## Жизненный цикл, владение и AOP

| Аспект | Контракт |
| --- | --- |
| Scope | Обычный Spring singleton на контекст; схема, лимиты и планировщик неизменяемы. Executor, ресурс БД и shutdown hook не создаются. |
| Смена настроек | Hot reload отсутствует. Осознанно заменяйте свой планировщик/контекст; существующие планы отчётов сохраняют исходную неизменяемую схему. |
| Значения параметров | SQL и коллекция параметров раздельны; изменяемые значения принадлежат вызывающему коду. Не меняйте их одновременно с выполнением JDBC. |
| Политика | Нет выполнения SQL, авторизации, транзакционного advice, retry, кэша и разрешения неоднозначности. Это задачи сервиса приложения. |
| Прокси | Стартер не включает AOP и не предоставляет аспект. Обычные final-beans работают без прокси. `proxyBeanMethods=false` в примере относится только к перехвату конфигурационных методов, а не переключает AOP приложения. |

Для независимой политики времени/аудита/транзакции размещайте AOP на сервисе приложения с подходящим интерфейсом либо non-final методами, допускающими прокси. Не применяйте широкий class-based pointcut к final JoinPlanner или static SqlRenderer. Вызывайте через прокси сервиса: self-invocation обходит advice. См. [официальные правила Spring](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html). Повтор детерминированной неоднозначности не является восстановлением и не должен молча выбирать маршрут.

## Ошибки и восстановление

| Ошибка/симптом | Причина | Действие |
| --- | --- | --- |
| No qualifying JoinPlanner bean | Нет Schema, несколько схем без основного кандидата, конфигурация выключена или стартер не обнаружен. | Предоставьте Schema/@Primary или свой планировщик, включите конфигурацию и проверьте JAR в runtime classpath. |
| Failed to bind properties under relational-paths | Числовое property имеет неверный формат или выходит за диапазон Java int. | Исправьте/удалите свойство. При активной автоконфигурации binding может выполняться и с собственным планировщиком/лимитами. |
| Invalid search bounds | Действующий лимит отрицателен либо max-tables равен нулю. | Используйте max-tables≥1, остальные≥0; @DefaultValue относится к отсутствующим свойствам, не неверным явным значениям. |
| Schema exceeds configured table/relationship limits | Выбранная схема превышает действующие SearchLimits. | Пересмотрите разрешённую схему либо осознанно увеличьте нужный лимит. |
| Search exceeded maxEdgeVisits=… | Автоматическое планирование просмотрело слишком много рёбер. | Сократите схему, задайте осознанные явные пути либо увеличьте лимит. Ошибка возникает в plan(); eager-bean отчёта поэтому может остановить запуск. |
| Ambiguous path to … | Метаданные дают два равных по стоимости варианта. | Перехватывайте AmbiguousPathException раньше PlanningException и выбирайте путь явно; не повторяйте вызов и не выбирайте свидетеля автоматически. |
| NoUniqueBeanDefinitionException for SearchLimits | Несколько собственных лимитов без одного кандидата для внедрения. | Пометьте один @Primary либо создайте собственные именованные планировщики с @Qualifier для схем и лимитов. |
| Cannot subclass final class / advice does not run | Pointcut приложения пытается создать class-прокси ядра либо сервис вызывает сам себя. | Размещайте advice на подходящей границе сервиса и вызывайте через его прокси; стартер не устанавливает аспект. |
| Could not find io.github.59alex:relational-paths:0.3.0 | Core-артефакт не установлен там, откуда его читает сборка. | Выполните publishToMavenLocal в соседнем core-репозитории; сохраните mavenLocal() у потребителя. Наличие в Central не подразумевается. |
| No matching Java installation / UnsupportedClassVersionError | Выбранный toolchain отсутствует либо версия runtime ниже минимальной для артефакта. | Используйте полный JDK 17+; default — версия текущей JVM. -PtestJavaVersion выбирает установленный toolchain; release/target остаются 17. |

## Покрытие примерами и границы

| Возможность | Запускаемый исходник / проверка |
| --- | --- |
| Штатное обнаружение Boot | [ReportApplication](../../src/examples/java/example/reports/ReportApplication.java) |
| Схема и составное tenant-равенство | [ReportConfiguration](../../src/examples/java/example/reports/ReportConfiguration.java) |
| Явный shipping-маршрут, переиспользуемый план, tenant/name-параметры | [OrderReport](../../src/examples/java/example/reports/OrderReport.java) |
| Defaults, отключение, @Primary, приоритет beans, startup-лимиты, ошибка поиска, SQL отчёта | [Контекстные тесты](../../src/test/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfigurationTest.java) |

`./gradlew test example` запускает существующие проверки и отчёт без выполнения SQL. Сначала установите соседний core [командами CONTRIBUTING](../../CONTRIBUTING.md). Стартер содержит регистрацию автоконфигурации и метаданные IDE; build создаёт source/Javadoc JAR. Java 17 — база API/байткода; запуск на 21/25/27 не добавляет специфических возможностей языка. Ограничения core неизменны: только INNER JOIN, нет гарантий кардинальности, защиты строк и автоматического обновления схемы.

Все типы, операторы и ошибки ядра описаны в [полном справочнике relational-paths](https://github.com/dlorian59/relational-paths/blob/main/docs/0.3.0/reference.ru.md). Состояние версий — в [каталоге](../index.md); команды Maven Local не означают доступность в Central.

[← Руководство](ru.md)

## Фасад запросов

Стартер также предоставляет RelationalQueries из выбранного JoinPlanner, если разрешим один планировщик и пользовательского bean RelationalQueries нет. Лимиты планировщика сохраняются. Это обычное внедрение зависимостей, не аспект и не другой планировщик. Прежнее условие единственной Schema остаётся.

RelationalQueries.from(root) возвращает неизменяемый Select: select(role.column...), whereEqual(column, value), where(column, operator, value), via(target, relationshipIds...), build(). build возвращает PlannedQuery с plan/statement/sql/bindings/explain. Цели выводятся из проекций и фильтров; повторные via и маршруты неиспользуемых целей отклоняются. Allowlist, неоднозначность, проверка маршрутов и лимиты делегируются core. Фасад не выполняет JDBC и не владеет ресурсами соединения/транзакции/отмены.

[Existing-project integration / Подключение к проекту](integration.ru.md) · [README](../../README.md)
