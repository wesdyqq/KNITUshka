# Сборка и запуск Android

## Окружение

Конфигурация сохранена при переносе проекта в `android/`:

| Компонент | Значение |
| --- | --- |
| JDK / Gradle daemon toolchain | 25 |
| Gradle Wrapper | 9.6.0, с проверкой SHA-256 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin Compose Compiler | 2.2.10 |
| Compile SDK | API 37, пакет `platforms;android-37.0` |
| Build Tools в CI | 36.0.0 |
| Target SDK | 37 |
| Minimum SDK | 24 (Android 7.0) |
| namespace / applicationId | `com.qq.knitushka` |

Установи Android Studio, совместимую с указанными AGP и SDK, и JDK 25. `gradle/gradle-daemon-jvm.properties` фиксирует версию toolchain. Параметры Java source/target 11 в модуле приложения не означают, что Gradle нужно запускать на JDK 11.

Установи SDK через SDK Manager или command-line tools:

```bash
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
sdkmanager --licenses
```

Путь к SDK Android Studio запишет в `android/local.properties`. Файл локальный и исключён из Git. Альтернатива — настроить `ANDROID_HOME` и `JAVA_HOME` в своём окружении.

## Android Studio

1. Склонируй репозиторий.
2. В **Open** выбери каталог `KNITUshka/android`.
3. Дождись Gradle Sync и установки недостающих SDK-компонентов.
4. Выбери Android-устройство или эмулятор API 24+ и запусти конфигурацию `app`.

Первый запуск приложения требует сети для выбора института и группы. Сохранённые недели затем доступны без сети.

## Командная строка

Из корня репозитория:

```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

В Windows: `gradlew.bat` вместо `./gradlew`.

- APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
- JVM-тесты: `android/app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint: `android/app/build/reports/lint-results-debug.html`.

Для единственного явно выбранного подключённого устройства:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Команда выполняется из `android/`. Серийный номер узнай через `adb devices`. Не удаляй установленную версию ради обхода несовпадающих подписей, не сохранив нужные данные.

## Тесты

`testDebugUnitTest` проверяет парсер на сохранённых HTML-страницах, удаление дублей, пустые и ошибочные ответы, границы дней и недель. Тесты не требуют живого сайта.

Instrumentation-тесты находятся в `app/src/androidTest` и требуют устройства/эмулятора:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Обычный GitHub CI запускает JVM-тесты, lint и сборку APK, но не instrumentation-тесты и не ручные проверки жестов/TalkBack.

## Известные ограничения окружения

- При первой сборке нужен доступ к Google Maven, Maven Central, Gradle и источнику JDK toolchain.
- Для локальной сборки без сети после загрузки зависимостей можно добавить `--offline`.
- В одном из локальных окружений наблюдался сбой JIT в JetBrains Runtime 25. При подобном `hs_err_pid*.log` попробуй актуальный OpenJDK/Temurin 25. Не добавляй crash dump в Git.
- Предупреждения lint об устаревших API сторонних lint-проверок не равны успешной проверке этих конкретных правил; такие сообщения нужно читать отдельно. Не отключай все проверки, чтобы скрыть предупреждения.

Подписанные APK собирает [Android Release](../.github/workflows/android-release.yml) по тегам версий, например `v1.1`. Ключ подписи хранится в GitHub Actions Secrets, не в репозитории.

### Личная сборка для Vivo

Не входит в публичный релиз и не собирается обычными задачами CI:

```bash
./gradlew :app:assembleVivo -PprivateVivoBuild=true
```

Это отдельная оптимизированная сборка без отладки с пакетом `com.example.tjktest.widgetdemoapp` и нативным пейджером. Работа проверена на одном Vivo; доступ зависит от списка разрешённых пакетов прошивки. Публичный `com.qq.knitushka` использует стандартный виджет с кнопками.

Если заданы все четыре переменные подписи `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, используется указанный ключ; иначе — локальный debug-сертификат для совместимости с установленной личной тестовой копией. Тип сборки при этом остаётся неотладочным. Разные сертификаты не позволяют обновление поверх существующей установки. Не публикуй этот APK как обычное обновление приложения.
