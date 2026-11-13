# КНИТушка — Android

Нативное Android-приложение на Kotlin + Jetpack Compose и Material 3.
Это корень Gradle-проекта: в Android Studio нужно открывать **эту папку**.

- [Описание и возможности приложения](../README.md)
- [Окружение, сборка и запуск](../docs/getting-started.md)
- [Архитектура, источник расписания и офлайн-кэш](../docs/architecture.md)
- [Подпись APK и релизы](../docs/releases.md)
- [Участие в разработке](../CONTRIBUTING.md)

Из этого каталога:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Нужны JDK 25 и Android SDK согласно [инструкции](../docs/getting-started.md).
Устанавливаемый debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Package name / applicationId сохранён: `com.qq.knitushka`. Лицензия — [MIT](../LICENSE).
