<div align="center">

<img src="docs/assets/icon.png" width="104" alt="Иконка КНИТУшки — заяц в университетской жилетке">

# КНИТУшка / KNITUshka

**Расписание КНИТУ под рукой — в том числе без интернета.**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](android/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](android/app/build.gradle.kts)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)](android/)
[![MIT License](https://img.shields.io/badge/License-MIT-862B3C)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/wesdyqq/KNITUshka?label=release)](https://github.com/wesdyqq/KNITUshka/releases/latest)
[![Android CI](https://github.com/wesdyqq/KNITUshka/actions/workflows/android-ci.yml/badge.svg)](https://github.com/wesdyqq/KNITUshka/actions/workflows/android-ci.yml)

[Скачать Android-версию](https://github.com/wesdyqq/KNITUshka/releases/latest) · [Сообщить об ошибке](https://github.com/wesdyqq/KNITUshka/issues/new/choose)

</div>

---

КНИТУшка — неофициальное open-source приложение для студентов Казанского национального исследовательского технологического университета. Выбери институт и группу при первом запуске — дальше приложение открывает расписание сразу.

Данные загружаются с [сайта КНИТУ](https://www.kstu.ru/www_GFgrid.jsp). Аккаунт и отдельный сервер не нужны.

## Возможности

- **Расписание по дням и неделям.** Предмет, время, аудитория, преподаватель и тип занятия в понятных карточках.
- **Перелистывание жестами.** Дни листаются вместе с колонкой пар, недели — в блоке дат. Вверх-вниз весь экран прокручивается вместе.
- **Работа без интернета.** Загруженные недели сохраняются на устройстве. После успешной загрузки приложение также запрашивает предыдущую, текущую и две следующие недели.
- **Проверка актуальности.** При открытии, восстановлении связи, вручную и периодически в фоне. Сбой сайта не удаляет последнее корректное расписание.
- **Обработка дублей.** Повторяющиеся записи одного временного слота объединяются; одинаковые пары, идущие подряд, остаются раздельными.
- **Текущая и следующая пара**, выбор группы и светлое, тёмное или системное оформление.
- **Виджет 2×3.** Краткое расписание выбранного дня, переключение дат кнопками, семь точек дней недели и открытие нужного дня в приложении. Через 10 минут возвращается к сегодняшнему дню; Android может задержать обновление во сне. На штатном рабочем столе Vivo добавляется вручную через меню виджетов.

> Первому запуску нужен интернет. Офлайн доступны только уже загруженные недели. Android может откладывать фоновые проверки. Время занятий — казанское (МСК).

## Скачать и установить

Открой [последний Android-релиз](https://github.com/wesdyqq/KNITUshka/releases/latest) и скачай APK из **Assets**, например `KNITUshka-v1.0.3.apk`.

1. На телефоне открой скачанный APK.
2. Если Android попросит, разреши установку из выбранного браузера или файлового менеджера.
3. Подтверди установку и выбери институт и группу.

Требуется **Android 7.0 или новее**. При переходе с локальной debug-версии на release Android может потребовать удалить старую установку из-за другой подписи; при этом локальные данные будут потеряны.

## Скриншоты

<div align="center">
<table>
  <tr>
    <td align="center" width="25%"><img src="docs/assets/screenshots/01-onboarding.webp" width="220" alt="Первый запуск: выбор института"></td>
    <td align="center" width="25%"><img src="docs/assets/screenshots/02-schedule.webp" width="220" alt="Расписание дня с карточками пар"></td>
    <td align="center" width="25%"><img src="docs/assets/screenshots/02b-schedule-scrolled.webp" width="220" alt="Тот же день, прокрученный ниже: аудитории и преподаватели"></td>
    <td align="center" width="25%"><img src="docs/assets/screenshots/03-no-classes.webp" width="220" alt="День без занятий"></td>
  </tr>
  <tr>
    <td align="center">Первый запуск</td>
    <td align="center" colspan="2">Расписание дня</td>
    <td align="center">День без пар</td>
  </tr>
</table>
</div>

## Структура репозитория

```text
KNITUshka/
├── android/                 # Kotlin + Jetpack Compose, Gradle Wrapper и тесты
├── ios/                     # Место для готовящейся iOS-версии
├── docs/                    # Сборка, архитектура, публикация релизов
├── .github/
│   ├── actions/             # Общее окружение Android-сборки
│   └── workflows/           # Android CI и подписанные релизы по тегам
├── README.md
├── LICENSE
└── CONTRIBUTING.md
```

## Roadmap

Реализованные возможности описаны выше. Дальше планируются:

- [ ] Расписание экзаменов.
- [ ] Напоминания перед парами.
- [ ] Экспорт в календарь.
- [ ] Дальнейшие проверки доступности и интерфейса на разных устройствах.
- [ ] iOS-версия на Swift + SwiftUI — готовится отдельно.

Эти возможности пока не доступны в Android-приложении.

## Участие в разработке

Баг-репорты, предложения и pull requests приветствуются. Прежде чем менять поведение приложения, опиши задачу в [Issues](https://github.com/wesdyqq/KNITUshka/issues).

[Правила участия →](CONTRIBUTING.md) · [Как устроено Android-приложение →](docs/architecture.md) · [Как выпускаются версии →](docs/releases.md)

## Лицензия

Код распространяется под [MIT License](LICENSE). Copyright © 2026 Камиль Сафьянов.

Приложение не является официальным сервисом КНИТУ. Расписание, названия и университетская символика принадлежат соответствующим правообладателям; MIT-лицензия проекта не изменяет их права и условия использования внешнего сайта.
