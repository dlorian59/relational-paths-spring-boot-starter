# 0.1.0 — integration guide

[Existing-project integration / Подключение к проекту](integration.en.md)

[← Versions](../index.md) · [README](../../README.md) · [Русский](ru.md)

[Complete API/configuration/error reference](reference.en.md)

The detailed guide is organized by application task:
[situation](integration.en.md#start-with-the-business-question),
[local setup](../../CONTRIBUTING.md),
[metadata and limits](integration.en.md#2-file-layout-and-approved-schema),
[business source and outcome](integration.en.md#3-service-fixed-policy-per-request-values), and
[boundaries](integration.en.md#6-failure-and-execution-boundaries).

This release adds property-based search limits, conditional JoinPlanner creation,
application bean precedence, the concise RelationalQueries facade, and a runnable order report with explicit address selection.
It targets Java 17 without preview APIs. No additional language feature is required on
21, 25 or 27. The core library remains framework-independent.
