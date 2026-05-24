# Secret Encryption — Implementation Plan

## Цель

Скрыть секретные свойства (`spring.datasource.username`, `spring.datasource.password`, `sample.api.key` и др.) из HTTP-ответа Config Server'а, возвращая их зашифрованными в формате `{cipher}...`. Клиентские приложения автоматически дешифруют через `encrypt.key`.

## Изменения

### 1. `src/main/resources/application.yml`

Добавить:
```yaml
encrypt:
  key: change-me-in-production
```

### 2. Файл `SecretEncryptingAdvice.java`

Пакет: `ru.postrf.vaultpoc.configserver`

`@ControllerAdvice`, реализующий `ResponseBodyAdvice<Environment>`.

**Подход:** `@Autowired(required = false) TextEncryptorLocator` (без `@ConditionalOnBean`).

**Почему не `@ConditionalOnBean(TextEncryptorLocator.class)`:** `SecretEncryptingAdvice` — обычный `@ControllerAdvice` (не auto-configuration). Spring Boot обрабатывает обычные компоненты РАНЬШЕ auto-configuration классов Spring Cloud Config. На момент проверки условия `@ConditionalOnBean` бин `TextEncryptorLocator` ещё не определён → advice не создавался → шифрования не было.

**Исправление:** `@Autowired(required = false)` — к моменту первого HTTP-запроса `TextEncryptorLocator` уже в контексте. Если не найден — advice логирует предупреждение и пропускает шифрование.

- `supports()` — true для `Environment.class`
- `beforeBodyWrite()` — проходит по всем `PropertySource`, находит ключи, совпадающие с паттернами:
  - `.*password.*`
  - `.*secret.*`
  - `.*\.key$`
  - `.*token`
  - `.*credential`
- Заменяет значение на `{cipher}encryptedValue`, используя `TextEncryptorLocator.locate(source)` — `SingleTextEncryptorLocator` игнорирует параметр
- Пропускает уже зашифрованные значения (начинающиеся с `{cipher}`)
- Try-catch с `log.error` для каждой ошибки шифрования
- **Включение/отключение:** `@ConditionalOnProperty(name = "config.server.secret-encryption.enabled", havingValue = "true", matchIfMissing = true)`

### 3. Клиентская `bootstrap.yml` (документация)

Добавить в README инструкцию: клиентское приложение должно иметь `encrypt.key` для автоматической расшифровки.

## Проверка

1. `mvn test` — 1 тест, `BUILD SUCCESS`
2. `spring-boot:run` — запустить Config Server
3. `curl http://localhost:8888/sample-service/default` — убедиться, что secret-ключи в ответе имеют вид `{cipher}...`
4. Проверить лог — для каждого зашифрованного ключа выводится `INFO Encrypted property: <key>`
5. Запустить клиентское приложение с `encrypt.key` — убедиться, что клиент получает расшифрованные значения

## Файлы

| Действие | Файл |
|---|---|
| Изменить | `src/main/resources/application.yml` |
| Создать | `src/main/java/.../configserver/SecretEncryptingAdvice.java` |
| Удалить | `src/main/java/.../configserver/SecretEncryptingConfig.java` |
| Обновить | `README.md` (раздел про клиентов) |
