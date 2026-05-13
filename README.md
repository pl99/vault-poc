# vault-poc — Spring Cloud Config Server + HashiCorp Vault

PoC реализация **Spring Cloud Config Server** с composite-backend: несекретные свойства приложений хранятся в файлах (native backend), секреты — в **HashiCorp Vault**. Vault запускается в Docker на том же хосте, что и Config Server, в dev-режиме. Клиентские сервисы в этом PoC **не реализованы** — реализован только сервер конфигурации и хранилище секретов.

## Архитектура

```
+----------------------+        HTTP        +-----------------------+
|  Client services     |  ----------------> |  Config Server        |
|  (не входят в PoC)   |   :8888/{app}/...  |  (Spring Boot)        |
+----------------------+                    +---+---------------+---+
                                                |               |
                                       native   |               | vault
                                       (file)   |               | (token auth)
                                                v               v
                                    +-----------------+   +-----------------------+
                                    | ./config-repo/  |   |  HashiCorp Vault      |
                                    |  *.properties   |   |  (dev mode, Docker)   |
                                    |  *.yml          |   |  KV v2 @ secret/      |
                                    +-----------------+   +-----------------------+
```

- Config Server: `http://localhost:8888`
- Vault: `http://localhost:8200`
- Vault dev root token: `root-token-poc` (только для PoC, **не использовать в проде**)

## Структура проекта

```
vault-poc/
├── docker-compose.yml          # Vault в dev-режиме
├── pom.xml                     # Spring Boot 3.3 + Spring Cloud Config Server
├── README.md
├── .gitignore
├── config-repo/                # native backend: несекретные свойства
│   └── application.properties  # общие свойства для всех приложений
├── scripts/
│   ├── init-vault.ps1          # PowerShell: записать тестовые секреты
│   └── init-vault.sh           # Bash: то же самое
└── src/main/
    ├── java/ru/postrf/vaultpoc/configserver/
    │   ├── ConfigServerApplication.java
    │   └── VaultTokenConfig.java    # ConfigTokenProvider bean (static VAULT_TOKEN)
    └── resources/
        └── application.yml
```

### Что куда класть

- **`config-repo/`** — несекретные свойства, отдаются native backend'ом. Имена файлов следуют шаблону Spring Cloud Config:
  - `application.properties` / `application.yml` — общие свойства для всех приложений;
  - `application-<profile>.properties` — общие свойства для профиля;
  - `<app-name>.properties` — свойства конкретного приложения (по умолчанию);
  - `<app-name>-<profile>.properties` — свойства приложения для профиля.
- **Vault** — только секреты (пароли, ключи, токены, сертификаты). См. `requires_secrets.md` в корне репозитория.

#### Пример: `spring.datasource.*`

Свойство `spring.datasource.url` — это не секрет (это адрес сервиса), его место — в `config-repo/application.properties` (или в `application-<profile>.properties`, если URL зависит от среды). Логин/пароль БД — секреты, их место — в Vault.

| Свойство                              | Где хранится                                 |
|---------------------------------------|----------------------------------------------|
| `spring.datasource.url`               | `config-repo/application.properties`         |
| `spring.datasource.driverClassName`   | `config-repo/application.properties`         |
| `spring.datasource.username`          | Vault, путь `secret/<app-name>[,profile]`    |
| `spring.datasource.password`          | Vault, путь `secret/<app-name>[,profile]`    |

Записать credentials в Vault для конкретного приложения (пример для `sample-service`, профиль `dev`):

```powershell
docker exec `
    -e VAULT_ADDR=http://127.0.0.1:8200 `
    -e VAULT_TOKEN=root-token-poc `
    vault-poc vault kv put "secret/sample-service,dev" `
        spring.datasource.username=app_user_dev `
        spring.datasource.password=s3cr3t-dev
```

> Важно: `vault kv put` **перезаписывает секрет целиком**. Если в этом же пути уже лежат другие ключи и их надо сохранить — используйте `vault kv patch` или передавайте полный набор ключей одной командой.

Клиентское приложение получит объединённый набор: `spring.datasource.url` из файла, `spring.datasource.username`/`password` из Vault. На стороне клиента (Spring Boot 2.4+) для этого достаточно указать:

```properties
spring.application.name=sample-service
spring.config.import=configserver:http://localhost:8888
spring.profiles.active=dev
```

## Требования

- JDK 17+
- Maven 3.9+ (можно использовать системный `mvn`)
- Docker + Docker Compose

## Запуск (PowerShell)

### 1. Поднять Vault в Docker

```powershell
cd C:\wrk\svn\post-rf\vault-poc
docker compose up -d
docker compose ps
```

Проверка, что Vault жив:

```powershell
curl http://127.0.0.1:8200/v1/sys/health
```

### 2. Залить тестовые секреты в Vault

```powershell
./scripts/init-vault.ps1
```

Скрипт создаёт KV v2 секреты:

| Путь в Vault                  | Назначение                                |
|-------------------------------|-------------------------------------------|
| `secret/application`          | общие свойства для всех приложений        |
| `secret/sample-service`       | `sample-service`, профиль по умолчанию    |
| `secret/sample-service,dev`   | `sample-service`, профиль `dev`           |
| `secret/sample-service,prod`  | `sample-service`, профиль `prod`          |

Разделитель имени приложения и профиля задан в `application.yml`:
`spring.cloud.config.server.vault.profile-separator: ','`.

### 3. Собрать и запустить Config Server

```powershell
mvn -f pom.xml clean package
java -jar target/config-server-0.0.1-SNAPSHOT.jar
```

или в dev-режиме:

```powershell
mvn spring-boot:run
```

## Проверка работы

Config Server отвечает по стандартным эндпоинтам Spring Cloud Config:

```
GET /{application}/{profile}[/{label}]
GET /{application}-{profile}.yml
GET /{application}-{profile}.properties
GET /{application}-{profile}.json
```

Примеры:

```powershell
# Конфигурация для sample-service, профиль default — должна вернуть секреты из
# secret/sample-service плюс общие из secret/application.
curl http://localhost:8888/sample-service/default

# Конфигурация для sample-service, профиль dev.
curl http://localhost:8888/sample-service/dev

# В формате properties / yml.
curl http://localhost:8888/sample-service-dev.yml
curl http://localhost:8888/sample-service-prod.properties
```

В ответе должны прийти ключи `spring.datasource.username`, `spring.datasource.password`,
`sample.api.key`, `app.global.message`, `app.global.owner` — с соответствующими профилю значениями, плюс свойства из `config-repo/application.properties` (`spring.datasource.url`, eureka, hikari, management, jackson, logging). Получается полный DataSource: URL приходит из файла, credentials — из Vault.

> Клиент **не обязан** передавать заголовок `X-Config-Token`: в composite-секции
> vault прописано `authentication: TOKEN`, на classpath лежит `spring-vault-core`,
> а сам токен поставляется через кастомный bean `ConfigTokenProvider`
> (`VaultTokenConfig.java`), который читает env var `VAULT_TOKEN`. Поле
> `composite[].vault.token` в YAML в composite-режиме не используется кодом —
> токен берётся **только** из bean'а `ConfigTokenProvider`. Без этого bean'а
> Config Server в composite-режиме падает с
> `IllegalArgumentException: Missing required header in HttpServletRequest: X-Config-Token`,
> потому что дефолтный `HttpRequestConfigTokenProvider` требует HTTP-заголовок.
> В проде используйте AppRole / Kubernetes auth и динамически выдаваемые токены
> вместо статического (тогда этот bean удаляется и `authentication:` меняется
> на нужный метод).

## Конфигурация через переменные окружения

`application.yml` читает следующие переменные (с дефолтами):

| Переменная       | Значение по умолчанию | Назначение                              |
|------------------|-----------------------|-----------------------------------------|
| `VAULT_HOST`     | `127.0.0.1`           | Хост Vault                              |
| `VAULT_PORT`     | `8200`                | Порт Vault                              |
| `VAULT_SCHEME`   | `http`                | Схема (`http`/`https`)                  |
| `VAULT_BACKEND`  | `secret`              | Путь монтирования KV-движка             |
| `VAULT_TOKEN`    | `root-token-poc`      | Токен для аутентификации Config Server  |

## Как Config Server собирает ответ (composite backend)

`spring.profiles.active: composite`, в `application.yml` подключены два источника. Spring Cloud Config обходит их в порядке объявления и **сливает PropertySource'ы**: первый в списке имеет высший приоритет.

```
composite:
  1. native  → ./config-repo/                ← несекретные свойства (выигрывают при коллизии)
  2. vault   → secret/ (KV v2)               ← секреты
```

### native backend

При запросе `GET /sample-service/dev` native backend ищет в `./config-repo/` файлы (в порядке приоритета, побеждает более специфичный):

1. `sample-service-dev.{properties,yml}`
2. `sample-service.{properties,yml}`
3. `application-dev.{properties,yml}`
4. `application.{properties,yml}` ← в текущем PoC лежит здесь

### vault backend

При том же запросе vault backend обращается к Vault по путям (в порядке приоритета, побеждает более специфичный):

1. `secret/data/sample-service,dev` — секреты приложения для профиля `dev`
2. `secret/data/sample-service`     — секреты приложения для профиля по умолчанию
3. `secret/data/application,dev`    — общие секреты для профиля `dev`
4. `secret/data/application`        — общие секреты (`default-key: application`)

Путь `secret/data/...` (а не `secret/...`) используется потому, что включён KV v2
(`kv-version: 2`).

## Остановка

```powershell
docker compose down
```

Так как Vault в dev-режиме хранит данные **только в памяти**, после остановки
контейнера все секреты будут потеряны — это нормально для PoC. Для постоянного
хранилища потребуется production-режим Vault с настройкой storage backend
(file, raft, consul и т.п.) — выходит за рамки PoC.

## Безопасность (важно для перехода из PoC в прод)

Текущий PoC намеренно упрощён и **не пригоден для продакшна**:

- используется dev-mode Vault (всё в памяти, единственный root-token, без TLS);
- root-token Vault зашит в `docker-compose.yml` и `application.yml`;
- Config Server общается с Vault по HTTP без TLS.

Перед использованием в реальной среде:

- развернуть Vault в production-режиме с постоянным storage и TLS;
- завести отдельную AppRole / k8s auth role для Config Server, выпускать
  короткоживущие токены, отказаться от статического `VAULT_TOKEN`;
- настроить TLS между Config Server и Vault, и между клиентами и Config Server;
- ограничить политиками Vault доступ к путям `secret/data/<app>` ровно для тех
  ролей/токенов, которые в этом нуждаются;
- защитить эндпоинты Config Server (basic auth / mTLS / OAuth2 resource server).
