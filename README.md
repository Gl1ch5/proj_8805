# Weather WebView

Простое Android-приложение погоды на WebView: только платформенный `android.webkit.WebView`, без Kotlin, AndroidX и внешних зависимостей. Интерфейс (HTML/CSS/JS) лежит в `app/src/main/assets/weather.html`, сетевые запросы выполняет нативный Java-код через `HttpURLConnection`, данные передаются в WebView через `JavascriptInterface`.

Источник данных: [Open-Meteo](https://open-meteo.com/) (лицензия CC BY 4.0). Ключ API не нужен. Работают геолокация (по разрешению), поиск города, прогноз на 7 дней и почасовой прогноз.

## Сборка

Локально Gradle и Android SDK не устанавливаются: сборка идёт только в GitHub Actions.

- Workflow: [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml)
- Триггеры: `push` в `main`, `push` тега `v*`, `pull_request`, `workflow_dispatch`
- Окружение CI: Ubuntu + JDK 17 + Gradle 8.7 + Android SDK 34 / build-tools 34.0.0 + AGP 8.5.2
- Результат: `WeatherWebView-debug.apk`, `WeatherWebView-release-unsigned.apk`, `SHA256SUMS.txt` в артефактах задачи
- При пуше тега `v*` создаётся GitHub Release с прикреплёнными APK

Для ручного запуска: в репозитории `Actions` -> `Android build (WebView weather)` -> `Run workflow`.

### Подпись release APK

`app-release-unsigned.apk` не подписан. Для установки на устройство:

```
keytool -genkeypair -v -keystore release.jks -alias weather -keyalg RSA -keysize 2048 -validity 10000
zipalign -p -f 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks release.jks --out WeatherWebView-release.apk aligned.apk
```

`WeatherWebView-debug.apk` уже подписан отладочным ключом и ставится сразу:

```
adb install -r WeatherWebView-debug.apk
```

## Структура

```
.github/workflows/android-build.yml  CI: JDK, SDK, Gradle, assemble, артефакты, release
app/src/main/assets/weather.html     Весь UI (офлайн, без CDN)
app/src/main/java/.../MainActivity.java      WebView + разрешения
app/src/main/java/.../WeatherBridge.java    HTTPS-запросы и JS-мост
app/src/main/res/                           Иконки, тема, layout, network security
app/build.gradle.kts                        MinSdk 23 / TargetSdk 34, ноль зависимостей
```

## Безопасность

- `cleartextTrafficPermitted="false"`, только HTTPS.
- `allowUniversalAccessFromFileURLs=false`, `allowFileAccessFromFileURLs=false`.
- `addJavascriptInterface` используется только для локального `file:///android_asset/weather.html`.
- Внешние `https://` ссылки открываются в системном браузере, не внутри WebView.
- JS-мост недоступен для других страниц: переходы внутри WebView запрещены.

## Совместимость

- Android 6.0 (API 23) и новее.
- Адаптивные иконки для API 26+, PNG-фолбэки для API 23-25.
- Тёмная тема UI реализована в HTML/CSS, системные цвета в `values` / `values-night`.
