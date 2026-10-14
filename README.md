# Trackly — трекер привычек и любых целей (Android)

Trackly — нативное Android-приложение (Kotlin + Jetpack Compose + Material 3), в котором пользователь
создаёт **любую** привычку или цель (вода, йога, чтение, сбережения, «без курения», свои варианты),
получает напоминания и видит прогресс: streak'и, календарь, статистику и недельный обзор.
Премиум-версия продаётся **только через Google Play Billing** (подписка).

* Данные привычек хранятся **локально** (Room). Приложение работает полностью офлайн.
* Нет аккаунтов, рекламы и аналитики, нет собственных сетевых запросов. Приложение само не объявляет `INTERNET`.
* Собственные разрешения: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`. В итоговый манифест
  библиотеки добавляют ещё `com.android.vending.BILLING`, `INTERNET`, `ACCESS_NETWORK_STATE`
  (Play Billing / AndroidX), `WAKE_LOCK` и `FOREGROUND_SERVICE` (WorkManager). Полный список и источник каждого
  разрешения печатает шаг «Inspect release» в CI.

> **Содержание**
> 1. [Быстрый старт](#1-быстрый-старт)
> 2. [Единое место настроек (package, версия, Product ID)](#2-единое-место-настроек)
> 3. [Сборка debug / release / AAB](#3-сборка-debug--release--aab)
> 4. [Подпись релиза (signing)](#4-подпись-релиза-signing)
> 5. [Google Play Console Setup (пошагово)](#5-google-play-console-setup)
> 6. [Тестирование Billing](#6-тестирование-billing)
> 7. [Как изменить package name, название, иконку](#7-как-изменить-package-name-название-иконку)
> 8. [Premium-функции: как изменить и добавить](#8-premium-функции)
> 9. [Цвета и темы](#9-цвета-и-темы)
> 10. [Добавление языка](#10-добавление-языка)
> 11. [Архитектура и структура проекта](#11-архитектура-и-структура-проекта)
> 12. [Тесты](#12-тесты)
> 13. [CI (GitHub Actions)](#13-ci-github-actions)
> 14. [Чек-лист перед публикацией](#14-чек-лист-перед-публикацией)
> 15. [Частые проблемы](#15-частые-проблемы)

---

## 1. Быстрый старт

**Требования:** Android Studio (Ladybug или новее), JDK 17 (поставляется с Android Studio), Android SDK 36.

1. `File ▸ Open…` → выберите корневую папку проекта (где лежит `settings.gradle.kts`).
2. Дождитесь Gradle Sync — зависимости скачаются автоматически (Google Maven + Maven Central).
3. Запустите конфигурацию **app** на эмуляторе или устройстве (API 26+), либо из терминала:

```bash
./gradlew installDebug          # собрать и установить debug-версию
./gradlew testDebugUnitTest     # unit-тесты
./gradlew lintRelease           # lint
```

Gradle wrapper (8.14.3) лежит в репозитории, отдельно ставить Gradle не нужно. Android SDK берётся из
`ANDROID_HOME` или `local.properties` (`sdk.dir=/path/to/Android/sdk`, Android Studio создаёт его сама).

В **debug**-сборке в `Settings` внизу есть блок *Debug*: загрузка демо-привычек и принудительное включение
Premium для проверки UI. Этого кода **нет в release** (он лежит в `app/src/debug`, а в `app/src/release`
только пустая заглушка), поэтому «кнопки сделать Premium» в продакшене не существует.

---

## 2. Единое место настроек

Всё, что вы меняете перед публикацией, находится в **`gradle.properties`** (корень проекта):

```properties
TRACKLY_APPLICATION_ID=com.example.trackly      # package name в Google Play (applicationId)
TRACKLY_VERSION_NAME=1.0.0                       # версия для пользователей
TRACKLY_VERSION_CODE=1                           # номер сборки: увеличивайте при каждой загрузке в Play
BILLING_PREMIUM_PRODUCT_ID=trackly_premium_monthly  # Product ID подписки из Play Console
PRIVACY_POLICY_URL=                              # ссылка на вашу политику (необязательно; иначе экран-заглушка)
TERMS_OF_SERVICE_URL=
```

* **Product ID находится здесь** и пробрасывается в код как `BuildConfig.BILLING_PREMIUM_PRODUCT_ID`
  (`billing/BillingConfig.kt`). Искать его по проекту не нужно.
* **Цена нигде не зашита.** Приложение читает цену и валюту из Google Play (`ProductDetails`:
  `formattedPrice`, `priceAmountMicros`, `priceCurrencyCode`, `billingPeriod`). Если вы поставите в Play Console
  $2.99/мес — пользователь в США увидит «Premium — $2.99 / month», а в других странах — локализованную цену.
* Значения можно переопределять через `-P` или `~/.gradle/gradle.properties`:
  `./gradlew bundleRelease -PTRACKLY_VERSION_CODE=7 -PTRACKLY_APPLICATION_ID=com.yourcompany.trackly`.

> ⚠️ **Google Play не принимает package name, начинающийся с `com.example`.** По вашему ТЗ в проекте оставлен
> placeholder `com.example.trackly`. Перед загрузкой в Play Console замените `TRACKLY_APPLICATION_ID` на свой.
> Менять исходники для этого **не нужно**: Kotlin-пакет (`namespace`) остаётся `com.example.trackly`, а Google Play
> видит только `applicationId`.

---

## 3. Сборка debug / release / AAB

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/ (подписанный, если задан keystore)
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab   <-- загружать в Google Play
```

Release включает R8 (minify + shrinkResources); правила — в `app/proguard-rules.pro`
(Room, Billing, WorkManager, DataStore несут свои consumer-rules).

Проверка перед загрузкой (то же делает CI):

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug assembleDebugAndroidTest bundleRelease
```

---

## 4. Подпись релиза (signing)

Keystore **никогда** не хранится в Git (`*.jks`, `*.keystore` в `.gitignore`). Gradle читает 4 значения из
переменных окружения **или** из `~/.gradle/gradle.properties`:

| Переменная          | Значение                                  |
|---------------------|-------------------------------------------|
| `KEYSTORE_PATH`     | путь к `.jks` (абсолютный или от корня проекта) |
| `KEYSTORE_PASSWORD` | пароль хранилища                          |
| `KEY_ALIAS`         | alias ключа                               |
| `KEY_PASSWORD`      | пароль ключа                              |

Если `KEYSTORE_PATH` не задан — debug собирается как обычно, а `bundleRelease` создаёт **неподписанный** AAB
(годится для проверки, но не для загрузки в Play).

### Создание upload-ключа

```bash
keytool -genkeypair -v -keystore ~/keys/trackly-upload.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Сохраните `.jks` и пароли в надёжном месте (менеджер паролей + резервная копия). Затем:

```bash
export KEYSTORE_PATH=~/keys/trackly-upload.jks
export KEYSTORE_PASSWORD='…'  KEY_ALIAS=upload  KEY_PASSWORD='…'
./gradlew bundleRelease
```

или добавьте в `~/.gradle/gradle.properties` (файл вне репозитория):

```properties
KEYSTORE_PATH=/home/me/keys/trackly-upload.jks
KEYSTORE_PASSWORD=…
KEY_ALIAS=upload
KEY_PASSWORD=…
```

Рекомендуется включить **Play App Signing** (по умолчанию для новых приложений): вы подписываете AAB
*upload-ключом*, а итоговым ключом подписи APK для пользователей управляет Google.

**Подпись в GitHub Actions:** добавьте секреты `KEYSTORE_BASE64` (`base64 -w0 trackly-upload.jks`),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — workflow сам соберёт подписанный AAB.

---

## 5. Google Play Console Setup

> Названия пунктов меню Play Console время от времени меняются; ориентируйтесь на смысл шагов.
> **Цена $2.99 не устанавливается автоматически — вы задаёте её сами в Play Console (шаг 5.5).**

### 5.1 Создайте приложение
1. [play.google.com/console](https://play.google.com/console) ▸ **Create app**.
2. Название (например, *Trackly*), язык по умолчанию, **App**, **Free** (подписка — это покупка внутри бесплатного
   приложения), примите декларации.

### 5.2 Настройте package name
1. Замените `TRACKLY_APPLICATION_ID` в `gradle.properties` на ваш уникальный id (например, `com.yourcompany.trackly`).
   Он должен совпадать с package name приложения в Play Console и **не может быть изменён после первой загрузки**.
2. Увеличьте `TRACKLY_VERSION_CODE` при каждой новой загрузке.

### 5.3 Загрузите первый AAB (нужно, чтобы создавать подписки)
1. Соберите подписанный AAB (раздел 4): `./gradlew bundleRelease`.
2. **Testing ▸ Internal testing ▸ Create new release** → загрузите `app-release.aab` → сохраните и опубликуйте
   релиз во внутреннем тестировании. (Play Console требует загруженный билд с Billing, прежде чем разрешит
   создавать подписки.)

### 5.4 Создайте subscription product
1. **Monetize with Play ▸ Products ▸ Subscriptions ▸ Create subscription**.
2. **Product ID:** `trackly_premium_monthly` — должен **точно** совпадать с `BILLING_PREMIUM_PRODUCT_ID`
   в `gradle.properties`. *Product ID нельзя изменить или переиспользовать после создания.*
3. **Name:** «Trackly Premium». Добавьте описание (отображается пользователю).

### 5.5 Настройте месячный base plan и цену $2.99
1. В подписке нажмите **Add base plan**: Base plan ID — например `monthly`, **Renewal type: Auto-renewing**,
   **Billing period: Monthly**. (Приложение выбирает подходящее предложение автоматически; если у подписки
   несколько base plan'ов/офферов, пользователю предлагается базовый план без offer ID либо оффер с бесплатным пробным
   периодом, на который он имеет право.)
2. **Set price** → выберите **основной регион (например, United States), цена `2.99 USD`** → при желании примените
   *Convert prices* для автоматического пересчёта в других валютах (или задайте цены регионов вручную).
3. Опционально: *Grace period*, *Account hold*, пробный период (free trial) через **Offers**. Приложение само покажет
   «Includes a N-day free trial», если пользователь имеет на него право.
4. **Activate** base plan и убедитесь, что подписка в статусе *Active*.

### 5.6 Настройте страны/регионы
В настройках base plan / приложения (**Release ▸ Setup ▸ Advanced settings ▸ Release countries / Availability**)
выберите страны, где доступна подписка и приложение, и проверьте цены по регионам.

### 5.7 Добавьте тестовых пользователей (license testers)
1. **Settings (все приложения) ▸ License testing** → добавьте Gmail-адреса тестеров. *License response:*
   `RESPOND_NORMALLY`. Покупки такими аккаунтами **бесплатны и не списывают деньги**.
2. **Testing ▸ Internal testing ▸ Testers** → создайте список e-mail тестеров, скопируйте **opt-in ссылку** и откройте
   её на тестовом устройстве под тестовым аккаунтом.

### 5.8 Создайте testing track и загрузите AAB
Для проверки Billing приложение должно быть установлено **из Google Play** (internal/closed testing), а не по `adb`:
* **Internal testing** — до 100 тестеров, публикуется почти мгновенно (шаг 5.3).
* **Closed testing** — для более широкой группы; перед **Production** Google может потребовать период закрытого
  тестирования для новых личных аккаунтов разработчика.

### 5.9 Проверьте Billing — см. раздел 6
* покупка подписки; * restore purchases; * отмена и истечение.

### 5.10 Завершение
Заполните *App content* (Privacy policy URL, Data safety, Ads = **No**, Content rating, Target audience),
листинг (иконка 512×512, скриншоты, описание) и отправьте релиз на проверку (Production).

---

## 6. Тестирование Billing

Что делает приложение (`billing/PlayBillingRepository.kt`): подключается к Google Play с повторными
попытками, получает `ProductDetails`, запускает покупку, обрабатывает отмену/ошибки/ожидание оплаты (pending),
**подтверждает (acknowledge) покупки**, вычисляет `PremiumEntitlement` (Active / Canceled-but-active / Pending / Expired / None),
хранит последнюю подтверждённую Google Play запись как офлайн-кэш (14 дней) и повторно проверяет покупки при
каждом открытии приложения.

**Сценарий проверки (устройство с тестовым аккаунтом, установка из Play через opt-in ссылку):**

| Проверка | Как |
|---|---|
| Цена отображается из Play | `Settings ▸ Premium` → экран Premium показывает цену из Play Console |
| Покупка | **Start Premium** → диалог Google Play → тестовая карта *Test card, always approves* → Premium разблокирован, снимается лимит 5 привычек |
| Pending | в диалоге выберите *Slow test card, approves after a few minutes* → приложение показывает «Purchase pending»; Premium включится после подтверждения |
| Отмена покупки | закройте диалог оплаты → «Purchase canceled», ничего не ломается |
| Restore | удалите и переустановите приложение (или очистите данные) → `Settings ▸ Restore purchases` |
| Отмена подписки | Google Play ▸ Payments & subscriptions ▸ Subscriptions ▸ Trackly Premium ▸ Cancel → Premium действует до конца оплаченного периода (статус «ends soon») |
| Истечение | у тестеров ускоренный цикл: месячная подписка продлевается каждые ~5 минут (несколько раз), затем истекает → приложение возвращается на Free-план |
| Нет интернета | Premium, подтверждённый ранее, продолжает работать офлайн до 14 дней; основной трекинг всегда работает офлайн |
| Manage subscription | `Settings ▸ Manage subscription` открывает страницу подписки в Google Play (отмена делается только там) |

Если экран Premium показывает «This subscription isn't available»: проверьте, что Product ID в Play Console
и в `gradle.properties` совпадают, подписка *Active*, приложение установлено из Play-трека, а аккаунт —
лицензионный тестер.

---

## 7. Как изменить package name, название, иконку

* **Package name (applicationId):** `TRACKLY_APPLICATION_ID` в `gradle.properties` (раздел 2). Этого достаточно.
  (Если захотите переименовать ещё и Kotlin-пакеты: `Refactor ▸ Rename` пакета `com.example.trackly` в Android Studio и
  поменять `namespace` в `app/build.gradle.kts` — это косметика, Google Play её не видит.)
* **Название приложения:** `app/src/main/res/values/strings.xml` → `app_name`. Для других языков — `values-xx/strings.xml`.
* **Иконка:** adaptive-иконка собрана из векторов:
  `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`
  (themed icon, Android 13+) и `res/mipmap-anydpi/ic_launcher*.xml`. Для сплэша — `drawable/ic_splash_icon.xml`,
  для уведомлений — `drawable/ic_stat_trackly.xml`. Для Google Play нужна ещё PNG-иконка 512×512
  (загружается в листинг, в AAB не входит). Быстрый путь: *Android Studio ▸ New ▸ Image Asset*.

---

## 8. Premium-функции

Вся логика «что бесплатно, что Premium» — в двух файлах, остальной код ничего не хардкодит:

* `domain/model/PremiumFeature.kt` — перечисление `PremiumFeature`, лимиты `FreeTier`
  (`MAX_ACTIVE_HABITS = 5`, `MAX_REMINDERS_PER_HABIT = 2`, `HISTORY_DAYS = 30`, `CALENDAR_MONTHS_BACK = 2`,
  статистика Free = только неделя) и `PremiumPolicy` (решения: можно ли создать привычку, выбрать цвет, открыть период и т. д.).
* `domain/logic/EntitlementResolver.kt` — превращает покупки Google Play в `PremiumEntitlement`.

| Free | Premium |
|---|---|
| до 5 активных привычек | безлимит привычек |
| базовая статистика (неделя), базовый календарь (3 месяца), история 30 дней | расширенные графики и месячная/годовая статистика, весь календарь, полная история |
| до 2 напоминаний на привычку | безлимит напоминаний |
| 8 базовых цветов, 2 темы-акцента | расширенная палитра + свой цвет, 8 тем-акцентов |
| — | Premium insights (лучший день, самая стабильная привычка, тренд) |

**Как добавить новую Premium-функцию:** (1) добавьте значение в `PremiumFeature`; (2) добавьте проверку в `PremiumPolicy`
(и тест в `PremiumPolicyTest`); (3) в UI вызовите `policy.isUnlocked(...)`/`gate(...)` и покажите `PremiumLockedCard`
(в `ui/components/PremiumLock.kt`) с `onUpgrade = { navigate(Routes.premium(feature)) }`; (4) добавьте строку
`premium_reason_*` в `strings_premium.xml` и ветку в `reasonText()` экрана Premium.

**Нельзя «сделать себя Premium»:** состояние берётся только из Google Play (`billing/PlayBillingRepository`).
Debug-переключатель существует лишь в debug-сборке (`app/src/debug/.../DebugTools.kt`). Архитектура допускает добавление
серверной проверки покупок позже (достаточно подставить токен покупки в свой backend внутри `PlayBillingRepository`).

---

## 9. Цвета и темы

Дизайн-система централизована в `app/src/main/java/com/example/trackly/ui/theme/`:

* `Color.kt` — нейтральные цвета светлой/тёмной темы (`Neutrals`) и **таблица акцент-тем** (`AccentTheme.colors()`).
* `Theme.kt` — `TracklyTheme`, семантические цвета (`ExtraColors`: success/streak/premium), формы, отступы (`Spacing`).
* `Type.kt` — типографика (шкала в `sp`, учитывает размер шрифта системы).
* Палитры цветов привычек: `domain/model/HabitColors.kt` (`FREE`, `PREMIUM`).

Тема: *Light / Dark / System* (Settings ▸ Appearance), акцент: Indigo/Teal бесплатно, остальные — Premium.

---

## 10. Добавление языка

1. Создайте `app/src/main/res/values-ru/` (или `values-de`, `values-fr`, `values-es`) и скопируйте туда все файлы
   `strings*.xml`, `legal.xml`, переведя тексты (plurals — в `strings.xml`).
2. Добавьте код языка в `res/xml/locales_config.xml` (`<locale android:name="ru" />`).
3. Добавьте его в `AppLocales.supportedTags` (`core/locale/AppLocales.kt`) — появится в `Settings ▸ Language`.
4. На Android 13+ язык также выбирается в системных настройках приложения.

Весь текст интерфейса берётся из string-ресурсов (в Kotlin-коде нет захардкоженных UI-строк, кроме эмодзи).
AAB настроен без языковых сплитов (`bundle.language.enableSplit = false`), чтобы переключение языка внутри
приложения работало.

---

## 11. Архитектура и структура проекта

Clean Architecture + MVVM, один Gradle-модуль `:app`, ручной DI (`di/AppContainer`) — без лишней магии.

```
app/src/main/java/com/example/trackly/
├── domain/            чистый Kotlin (без Android) — легко тестируется
│   ├── model/         Habit, HabitSchedule, Reminder, PremiumEntitlement, PremiumPolicy, настройки…
│   ├── logic/         StreakCalculator, StatsCalculator, GoalProgress, EntitlementResolver, HabitValidator, ReminderTimeCalculator
│   ├── repository/    интерфейсы репозиториев
│   └── usecase/       SaveHabitUseCase (валидация + лимиты Free/Premium)
├── data/              Room (entities, DAO, database, mappers) и DataStore (SettingsRepositoryImpl)
├── billing/           PlayBillingRepository (Google Play Billing 8), BillingConfig, контракт BillingRepository
├── notifications/     каналы, AlarmManager-планировщик, ресиверы, WorkManager-страховка, deep link в уведомлении
├── presentation/      экраны Compose + ViewModel: home, habit (форма/детали/история/reorder), calendar,
│                      stats (+ weekly), settings, premium, onboarding, legal, common
├── navigation/        Routes, AppNavHost, нижняя навигация
├── ui/theme, ui/components   дизайн-система и переиспользуемые компоненты (кольцо прогресса, графики, календарь…)
├── core/              время (TodayProvider, SystemClock), локаль, утилиты
└── di/                AppContainer
app/src/debug/        DebugTools (демо-данные, форс-Premium) — только debug
app/src/release/      пустая заглушка DebugTools
app/src/test/         unit-тесты (JVM)
app/src/androidTest/  UI-тесты Compose и тесты Room/репозитория
```

**Ключевые решения**

* **Streak-движок** (`StreakCalculator`) учитывает расписание: незапланированные дни не ломают streak, пропущенный
  запланированный день — ломает; «сегодня» ещё не выполнено ≠ пропуск; паузы привычки не ломают streak.
* **Напоминания**: `AlarmManager.setAndAllowWhileIdle` (без exact-alarm разрешений, работает при закрытом приложении),
  после срабатывания планируется следующее; `BootReceiver` восстанавливает будильники после перезагрузки/обновления;
  `WorkManager` раз в 12 часов страхует от потерянных будильников. Уведомление не показывается, если привычка уже
  выполнена сегодня. Нажатие открывает **Habit Details** этой привычки. Звук/вибрация выбираются через 4 канала уведомлений.
* **Offline-first**: все данные и логика локальные; интернет нужен только Google Play Billing.
* **Ошибки**: каждый экран имеет Loading / Empty / Error (с повтором); сбои БД, уведомлений и биллинга не роняют приложение.
* **Дата «сегодня»** обновляется в полночь и при смене даты/часового пояса (`TodayProvider`).

---

## 12. Тесты

```bash
./gradlew testDebugUnitTest              # JVM unit-тесты (127)
./gradlew connectedDebugAndroidTest      # UI-тесты + Room на эмуляторе/устройстве
```

Unit-тесты покрывают: расчёт streak, расписание, прогресс цели/completion, даты и диапазоны, статистику,
валидацию формы, расчёт времени напоминаний, **Premium entitlement и политику Free/Premium**, `SaveHabitUseCase`
(лимиты) и ViewModel'и (используются fake-репозитории; `FakeBillingRepository` — только в тестах).

UI-тесты (Compose): создание привычки, валидация формы, выполнение boolean/counter привычек, редактирование,
архивирование, удаление с подтверждением, навигация по вкладкам, экран Premium, лимит Free-плана.
Тесты Room проверяют реальную схему БД на in-memory базе (каскадное удаление, паузы, порядок, streak).

**Миграции БД.** Схема версии 1 закоммичена в `app/schemas/…/1.json` (экспорт Room). Если вы меняете сущности,
повысьте `version` в `TracklyDatabase`, напишите `Migration` (деструктивная миграция отключена намеренно —
история привычек пользователей не должна теряться) и закоммитьте новый файл схемы; CI падает, если схема
изменилась, а файл не обновлён.

---

## 13. CI (GitHub Actions)

* `.github/workflows/android.yml` — на каждый push: unit-тесты, lint, debug APK, компиляция androidTest,
  `bundleRelease`, печать итоговых разрешений из merged-манифеста, SHA-256 и состава AAB. Артефакты
  (**`trackly-release-aab`**, `trackly-debug-apk`, `reports`) скачиваются со страницы запуска
  (*Actions ▸ Android CI ▸ выбрать запуск ▸ Artifacts*). Без секретов подписи AAB получается **неподписанным** —
  такой файл нельзя загрузить в Google Play (см. раздел 4).
* `.github/workflows/instrumented.yml` — UI-тесты и тесты Room на эмуляторе (API 34) на каждый push.

---

## 14. Чек-лист перед публикацией

- [ ] `TRACKLY_APPLICATION_ID` заменён (не `com.example.*`), `TRACKLY_VERSION_CODE` увеличен.
- [ ] Создана подписка с Product ID = `BILLING_PREMIUM_PRODUCT_ID`, base plan активирован, цена задана вами.
- [ ] Свой upload keystore подключён (раздел 4); AAB подписан; включён Play App Signing.
- [ ] Заменены юридические тексты: `res/values/legal.xml` (TODO-маркеры) **или** заданы `PRIVACY_POLICY_URL` / `TERMS_OF_SERVICE_URL`.
- [ ] В Play Console: Privacy Policy URL, Data safety (данные не собираются и не передаются; покупки обрабатывает Google Play),
      Ads = No, Content rating, Target audience, декларация о подписке.
- [ ] Проверены покупка / restore / отмена / истечение на тестовом аккаунте (раздел 6).
- [ ] Иконка 512×512, скриншоты, описание листинга.
- [ ] `./gradlew testDebugUnitTest lintRelease bundleRelease` проходит.

---

## 15. Частые проблемы

* **Экран Premium: «Google Play billing is not available»** — эмулятор без Google Play, нет входа в аккаунт Google,
  или приложение установлено через `adb`, а не из Play-трека.
* **«This subscription isn't available»** — Product ID не совпадает / подписка не активна / нет опубликованного билда с Billing.
* **Не приходят напоминания** — Android 13+: разрешите уведомления (Settings ▸ Notifications подскажет); на некоторых
  устройствах отключите агрессивную экономию батареи для приложения.
* **`bundleRelease` создаёт unsigned AAB** — не заданы `KEYSTORE_*` (раздел 4).
* **Gradle не находит SDK** — задайте `ANDROID_HOME` или `sdk.dir` в `local.properties`.
