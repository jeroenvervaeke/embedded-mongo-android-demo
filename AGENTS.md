# AGENTS.md

Offline coffee-place finder for Ireland. MongoDB's own engine
([embedded-mongodb](https://github.com/jeroenvervaeke/embedded-mongo)) runs in-process; no server,
no network call.

## Commands

```sh
export JAVA_HOME=/path/to/jdk21          # Android Studio's bundled runtime qualifies
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew :data:test                     # no emulator and no engine needed
./gradlew :app:testDebugUnitTest         # the screen tests among them
./gradlew :app:lintDebug                 # warningsAsErrors is on
./gradlew :app:assembleDebug             # needs the library's engine to link
```

Run all four before claiming a change works.

## Modules

| | |
| --- | --- |
| `data/` | Plain Kotlin. Pipelines, parsing, seeding, screen state. No Android, no engine. |
| `data/query/` | Every pipeline and both index specs. |
| `app/engine/` | `EmbeddedMongoOpener`, the whole of the native contact. |
| `app/location/` | Fused provider, a 10 s budget, Dublin as fallback. |
| `app/ui/console/` | The map screen: canvas, console readout, and the sheet the list lives in. |
| `app/ui/explorer/` | The pipeline editor, and what the engine answered. |
| `app/ui/about/` | Hero, measured figures, category donut, credits, licence texts. |
| `app/assets/places/` | Seed, attribution, licence texts. |

The screens are tested with the rest: `androidx.compose.ui:ui-test-junit4` under Robolectric, so
`:app:testDebugUnitTest` covers the UI and still needs no emulator. `app/src/test/resources/
robolectric.properties` sets a phone-sized device: the default 320x470 at density 1 is too small
for a sheet that opens over a map, and layouts fail there for reasons no phone has.

`:data` depends on `embedded-mongodb-core`, the library's Android-free half. Every collection sits
on the library's `CommandRunner`, one suspending method wide, so all of `:data` is tested on the
JVM against a scripted one.

Own only the coffee-specific part: pipelines, index specs, parsing. The `aggregate`/`insert`
commands, cursor paging, `_id` generation and write-result checking are the library's: do not
reimplement them.

## Library dependency

Consumed as a Gradle **included build**, not a Maven artifact, from `../embedded-mongo/android`.
Override with the `embeddedMongoAndroidDir` property. Two substitutions: `:app` takes the AAR,
`:data` takes the core module alone.

Requires a library checkout at `808276e` or later, or the release build fails on
`Missing class org.slf4j.Logger`.

Both builds are pinned to the same AGP and Kotlin: a composite build compiles them with one of
each. AndroidX is pinned to what AGP 8.13.2 and `compileSdk` 36 accept; newer demands AGP 9.1.

## Screens

Three tabs: MAP, EXPLORER, ABOUT. The map is the application: a full-bleed canvas with the
console over it and the result sheet under it.

- **The headline is a `$count`, not `places.size`.** The list stops at fifty documents and the
  radius does not, so two queries answer one screen: [`PlaceRepository.count`] appends `$count` to
  the same selection the list was built from, and both come from one `NearbyFinder.Request`. A
  number and a list that were built from different questions is the one bug this screen cannot
  survive.
- **A minimised map shows the map, the count and the radius.** The search box, the category chips,
  the `RESULT SET` header and the HUMAN/BSON switch all sit below the peek, which is below the
  bottom edge. `ConsoleMapScreenTest` holds that.
- **The console is the top of the screen.** `coffee.places`, the pipeline that produced what is on
  screen, and what it cost, read out of the command the reply carried, never rebuilt for display.
- **No screen shows a number that was written down.** The about screen's donut is a `$group` the
  engine ran while the screen opened, and its timings are what *this* launch cost on *this* phone,
  reported by `StartupTimer`. `DocumentRow` prints a place through `Place.asDocument`, which a
  round-trip test holds to the shape the seed stored. Nothing here needs maintaining as devices
  change.
- **The explorer runs what was typed, against the same collection.** An unsupported stage comes
  back as MongoDB's refusal, and `EXPLAIN` is the engine's plan; a client-side guess at which
  index served a query would be the one figure on the screen that nothing checked.
- **The two map buttons are icons, bottom right.** A crosshair frames the radius, an Ireland
  outline frames the island; both are drawn in code, the island from a coarse ring of real
  coordinates projected the way the map projects a place. Nothing over the map is a word.
- **No font files, bundled or fetched.** Monospace for anything the engine said, the platform's
  sans for anything a person wrote, serif for the headline count. An application whose point is
  that it makes no network call has no business downloading a typeface.

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
- **`minSdk` is 28**, not the library's 24: the engine crashes below Android 9.0.
- **Free-disk floor is 64 MiB** (`CoffeeDatabase.COFFEE_STORAGE`), not MongoDB's 500 MB. At the
  default, a phone with 400 MB free opens the database and never finishes seeding. Do not lower
  it: WiredTiger answers a genuinely full disk by aborting the process, so the floor is the only
  warning there is.
- **Backup stays off.** `allowBackup="false"` plus `data_extraction_rules.xml` excluding `coffee`
  from device transfer. A WiredTiger directory restored elsewhere is corrupt, not migrated.
- **`$text` and `$geoNear` cannot combine.** Both must lead a pipeline, and `$geoNear`'s `query`
  will not take a `$text`. Search matches on the text index, then measures with a haversine in
  `$sin`/`$asin`/`$degreesToRadians`: same sphere, so the two are comparable.
- **`:data` is a plain JVM module, so Lint never checks its API levels.** Anything it calls must
  exist on API 28, and `InputStream.readNBytes` does not (API 33).
- **Add no R8 rules here.** `proguard-rules.pro` is comment-only; everything needed comes from the
  library's `consumer-rules.pro`.
- **One APK per ABI**, arm64-v8a and x86_64. A universal APK would ship both 46 MiB engines. The
  `include` list is also what stops a 32-bit split, which MongoDB cannot build.

## Measuring

`adb logcat -s CoffeeTimings`: every query, start-up phase and location attempt logs one line,
which is how the phone is asked what something cost. Nothing in this repository records those
numbers: they belong to a device, a build type and a page cache, and a figure written down here
is a figure that is wrong by the next phone. Measure when you care, on a release build, on an
awake and unlocked device.

## State

Ships and runs. Driven on a Galaxy S23 Ultra: cold start, the map and its radius, the list, the
explorer and its `EXPLAIN`, and the about screen. Tested on the JVM as well, with `:data:test` and
`:app:testDebugUnitTest`, screens included.

The release build is signed with the debug keystore, so `assembleRelease` produces an APK that
installs; there is no release key, so Play App Signing, key rotation and Play Integrity are out
of reach.

One thing is open: `$geoNear` has never run from a fix outside the seed's bounding box. Emulators
do not deliver one (`adb emu geo fix` leaves fused at `last location=null`), so it needs a real
device outside Ireland.
