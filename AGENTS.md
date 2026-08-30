# AGENTS.md

Offline coffee-place finder for Ireland. MongoDB's own engine
([embedded-mongodb](https://github.com/jeroenvervaeke/embedded-mongo)) runs in-process; no server,
no network call.

## Commands

```sh
export JAVA_HOME=/path/to/jdk21          # Android Studio's bundled runtime qualifies
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew :data:test                     # 140 tests, no emulator and no engine needed
./gradlew :app:testDebugUnitTest         # 58 tests
./gradlew :app:lintDebug                 # warningsAsErrors is on
./gradlew :app:assembleDebug             # needs the library's engine to link
```

Run all four before claiming a change works.

## Modules

| | |
| --- | --- |
| `data/` | Plain Kotlin. Pipelines, parsing, seeding, screen state. No Android, no engine. |
| `data/query/` | Every pipeline and both index specs. |
| `app/engine/` | `EmbeddedMongoOpener` — the whole of the native contact. |
| `app/location/` | Fused provider, a 10 s budget, Dublin as fallback. |
| `app/ui/` | Compose: list, map, pipeline, about. |
| `app/assets/places/` | Seed, attribution, licence texts. |

`:data` depends on `embedded-mongodb-core`, the library's Android-free half. Every collection sits
on the library's `CommandRunner`, one suspending method wide, so all of `:data` is tested on the
JVM against a scripted one.

Own only the coffee-specific part: pipelines, index specs, parsing. The `aggregate`/`insert`
commands, cursor paging, `_id` generation and write-result checking are the library's — do not
reimplement them.

## Library dependency

Consumed as a Gradle **included build**, not a Maven artifact, from `../embedded-mongo/android`.
Override with the `embeddedMongoAndroidDir` property. Two substitutions: `:app` takes the AAR,
`:data` takes the core module alone.

Requires a library checkout at `808276e` or later, or the release build fails on
`Missing class org.slf4j.Logger`.

Both builds are pinned to the same AGP and Kotlin — a composite build compiles them with one of
each. AndroidX is pinned to what AGP 8.13.2 and `compileSdk` 36 accept; newer demands AGP 9.1.

## Rules

Each one is load-bearing. Breaking any is silent.

- **Seed asset is `ireland.bson.gzip`, never `.gz`.** AGP's merger gunzips a `.gz` asset and drops
  the extension; the app then dies on `FileNotFoundException`. `ShippedAssetsTest` reads what the
  merger produced, not the source tree.
- **Count with `countDocuments`, never `estimatedDocumentCount`.** The metadata count is wrong
  after an unclean shutdown, which is what Android killing the process mid-seed is.
- **Open the engine in the application scope, not `viewModelScope`.** Leaving a screen mid-open
  otherwise throws away a started engine. The scope is a `SupervisorJob` so a failed open does not
  kill it.
- **Nothing touches the engine on the main thread.** The suspending API dispatches to the
  library's database thread; the blocking one throws if called from main.
- **`minSdk` is 28**, not the library's 24 — the engine crashes below Android 9.0.
- **Free-disk floor is 64 MiB** (`CoffeeDatabase.COFFEE_STORAGE`), not MongoDB's 500 MB. At the
  default, a phone with 400 MB free opens the database and never finishes seeding. Do not lower
  it: WiredTiger answers a genuinely full disk by aborting the process, so the floor is the only
  warning there is.
- **Backup stays off.** `allowBackup="false"` plus `data_extraction_rules.xml` excluding `coffee`
  from device transfer. A WiredTiger directory restored elsewhere is corrupt, not migrated.
- **`$text` and `$geoNear` cannot combine.** Both must lead a pipeline, and `$geoNear`'s `query`
  will not take a `$text`. Search matches on the text index, then measures with a haversine in
  `$sin`/`$asin`/`$degreesToRadians` — same sphere, so the two are comparable.
- **`:data` is a plain JVM module, so Lint never checks its API levels.** Anything it calls must
  exist on API 28 — `InputStream.readNBytes` does not (API 33).
- **Add no R8 rules here.** `proguard-rules.pro` is comment-only; everything needed comes from the
  library's `consumer-rules.pro`.
- **One APK per ABI**, arm64-v8a and x86_64. A universal APK would ship both 46 MiB engines. The
  `include` list is also what stops a 32-bit split, which MongoDB cannot build.

## Measuring

`adb logcat -s CoffeeTimings` — every query and start-up phase logs one line. `ReportDrawnWhen`
covers process start and dex loading, which the system prints as `Fully drawn`.

Measure release builds only; debug is 2–3× slower on these paths. Wake **and unlock** the device
first and check `dumpsys power | grep mWakefulness` says `Awake` — a dozing phone clocks its big
core at 864 MHz and every number is wrong.

## State

Ships and runs. The release build is signed with the debug keystore, so `assembleRelease`
produces an APK that installs; there is no release key, so Play App Signing, key rotation and
Play Integrity are out of reach.

Measured on a Galaxy S23 Ultra (SM-S918B, arm64, API 36), release build, unlocked, battery saver
off, on the collection API. Eight cold starts with the data cleared between each, five warm:

| | |
| --- | --- |
| Engine open | 350 ms – 3.05 s |
| Insert 5,180 documents | 240–493 ms |
| Both indexes | 80–120 ms |
| Cold start, places ready | 0.68–3.64 s |
| Warm start, places ready | 0.32–0.63 s |
| `$geoNear`, 50 documents | 15–38 ms |
| `$geoWithin`, all 5,180 | 71–114 ms |

**Engine open is the whole of the variance, and it is page cache.** The first three runs after
the phone had been idle took 2.28–3.05 s; once the 46 MiB library was in cache the same run took
350–670 ms. Quote the cold figure for a first launch after a reboot and the warm one for
everything else. An earlier round of measurements put this at 0.3–1.2 s, which is the warm
number alone.

The location budget expires at 9.94 s by the `MONITOR_LOCATION` app op. `dumpsys location` will
not show it: the fused provider attributes the request to `com.google.android.gms` and marks it
`hiddenFromAppOps`.

Two conditions decide whether a measurement means anything, and both bite over `adb`. A locked
phone dozes and clocks its big core at 864 MHz. Battery saver does the same more quietly — check
`settings get global low_power`. Check `dumpsys power | grep mWakefulness` between runs, not just
before them: a 30-second screen timeout re-dozes the phone mid-session.

One thing is open: `$geoNear` has never run from a fix outside the seed's bounding box.
Emulators do not deliver one — `adb emu geo fix` leaves fused at `last location=null` — so it
needs a real device outside Ireland.
