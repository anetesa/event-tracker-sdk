# Event Tracker SDK

Домашнее техническое задание: Android SDK для трекинга событий (`:eventtrackersdk`, упакован как
модуль Android Library) вместе с демо-приложением (`:app`), которое его прогоняет через все
сценарии.

## Модули

- **`:eventtrackersdk`** — сам SDK. Никакого Hilt, никакого Compose, никакой зависимости от
  DI-фреймворка какого бы то ни было вида — самодостаточная, drop-in библиотека. Единственные её
  зависимости — Room, WorkManager и kotlinx-coroutines.
- **`:app`** — демо-приложение: Kotlin, Jetpack Compose (Material 3), Hilt, один экран.

## Публичный API SDK

```kotlin
EventTrackerSDK.init(context, retentionDays = 7, maxEventCount = 100) // один раз на процесс; последующие вызовы ничего не делают
EventTrackerSDK.updateConfig(retentionDays = 14, maxEventCount = 200) // обновление на лету, без повторной инициализации
EventTrackerSDK.track("button_clicked", mapOf("button_id" to "submit")) // неблокирующий, потокобезопасный
EventTrackerSDK.getRecentEvents(limit = 50) // Flow<List<TrackedEvent>>, сначала новые
EventTrackerSDK.getStatistics() // suspend, общее/сегодняшнее количество и разбивка по дням
EventTrackerSDK.clearAllEvents() // suspend, удаляет только те события, которые UI уже показал
```

## Заметки по дизайну

- **Потокобезопасность и неблокирующий `track()`**: SDK держит собственный
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Вызов `track()` просто запускает корутину
  в этом scope и немедленно возвращает управление вызывающему коду — сериализацией самих записей
  под капотом занимается Room.
- **Идемпотентный `init()`**: double-checked locking (блок `synchronized` плюс флаг `@Volatile`,
  публикуемый последним) означает, что побеждает тот вызов, который первым достиг процесса;
  каждый последующий вызов — даже конкурирующие вызовы из других потоков — просто игнорируется.
- **Правило "seen"**: `EventEntity.isSeen` начинается со значения `false` и становится `true`
  только после того, как порция строк реально дошла до подписчика `getRecentEvents()` — другими
  словами, после того, как UI их показал. `clearAllEvents()` удаляет исключительно строки, где
  `isSeen = true`. Поскольку отображаемый список демо ограничен настроенным максимальным
  количеством событий, стресс-трекинг, толкающий общее число за этот потолок, оставляет самые
  старые лишние строки по-настоящему непросмотренными — и, следовательно, доказуемо защищёнными
  от "Clear All". Полное обоснование — включая то, почему автоматическая очистка по
  retention/количеству сознательно игнорирует этот флаг — находится в doc-комментариях
  `EventsViewModel` и `CleanupWorker`.
- **WorkManager**: `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)` с
  периодом в 24 часа и начальной задержкой в 1 час. Политика `KEEP` гарантирует, что повторная
  инициализация SDK при перезапусках процесса никогда не порождает дублирующую задачу.
- **Кодирование JSON**: properties кодируются через небольшой написанный вручную кодек
  (`PropertiesJsonCodec`), а не через `org.json.JSONObject`. Properties — это всегда плоская
  `Map<String, String>`, а настоящая Android-реализация `org.json` бросает исключение под
  чистым JUnit (сотрудничает только с Robolectric), что означало бы тащить более тяжёлую
  тестовую настройку без реальной пользы в этом случае.

## Сборка и тестирование

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Лог разработки с участием AI — в `AI_COMMUNICATION.md`; известные пробелы отслеживаются в
`TODO.md`.
