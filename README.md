# Coffee Offline

Every coffee place on the island of Ireland, searchable on a phone with the aeroplane mode
switch on. No server, no network call, no API key, and no map SDK.

The database is [embedded-mongodb][library] — MongoDB's own query engine and WiredTiger,
compiled into the application process. The queries are the ones a server would run: `$geoNear`
over a `2dsphere` index for what is nearest, `$text` for search, `$geoWithin` with a polygon for
what the map is showing. There is a screen that prints the exact command behind whatever is on
screen, because that is the only way to tell a real engine from a hand-rolled index with a
MongoDB-shaped name on it.

The map is drawn on a `Canvas` from the coordinates the query returned. There are no tiles under
it. Ireland is recognisable because five thousand coffee places are enough to draw its towns and
its coast road — the shape is the data.

## This project is not a MongoDB product

Not supported by, endorsed by, or affiliated with MongoDB, Inc. No MongoDB logo or branding is
used here. It is an independent demonstration of an embedded build of the engine.

## Data, attribution and licences

Places come from the [Overture Maps Foundation](https://overturemaps.org), release 2026-08-19.0,
accessed 2026-08-27. **The data has been modified**: filtered to four coffee categories inside a
bounding box around Ireland, and reshaped into the documents this application stores.

© 2026 Foursquare Labs, Inc. All rights reserved.

Contributing datasets are covered by CDLA-Permissive-2.0, the Apache License 2.0 with the
Foursquare NOTICE, and CC0-1.0. **All four texts ship inside the application** — in
`app/src/main/assets/places/licenses/`, shown in full on the About screen — because naming a
licence does not satisfy it: CDLA-Permissive-2.0 section 2.1 and the NOTICE both require the text
to travel with the data. CC0-1.0 imposes no such condition and is shipped anyway, so that every
licence the data is under can be read inside the application.
`app/src/main/assets/places/ireland.attribution.txt` records the extraction and every
modification made to the data, and `licenses/SOURCES` records where each text was fetched from.

The seed itself is `app/src/main/assets/places/ireland.bson.gzip`: 5,180 documents of
concatenated BSON, gzipped to 454 KB, produced by the library's `scripts/build-places-seed`. The
extension is `.gzip` rather than the `.gz` that script writes, and that matters — see "Things
worth knowing".

## What is built, and what runs

It runs, on a phone. Built against the published engine (`native-81be76197da4`) with no override,
and driven end to end on a **Galaxy S23 Ultra (SM-S918B, arm64-v8a, Android 16 / API 36)** as well
as on an API 35 x86_64 emulator.

| | |
| --- | --- |
| `./gradlew :data:test` | **green.** 140 tests, no Android SDK needed |
| `./gradlew :app:testDebugUnitTest` | **green.** 55 tests — the real seed, the packaged assets, the engine lifecycle, the location budget, the timings |
| `./gradlew :app:lintDebug` | **green**, with `warningsAsErrors` on |
| `./gradlew :app:assembleDebug` | **84 MB** per ABI (debug stores dex uncompressed) |
| `./gradlew :app:assembleRelease` | **59 MB** per ABI, R8 on |
| On arm64 hardware | seeds, indexes, queries, draws, and survives being killed with the engine open |

The arm64 APK carries the published `aarch64-linux-android` engine byte for byte — the `.so` in
`lib/arm64-v8a/` has the SHA-256 the library's `prebuilt.rs` records — and it loads and runs on
the phone with nothing about it patched for the architecture.

### What the phone showed

Numbers below are the **release build**, five runs each, screen on and battery saver off. The
application prints them itself: `adb logcat -s CoffeeTimings`.

Both conditions are load-bearing, and neither is obvious when driving a phone over `adb`. A
locked phone dozes: `am start` still launches, the log lines still appear, and every one of them
is wrong, because the big core sits at 864 MHz instead of its 3.36 GHz — measured here as a
start-up of 5.6 s against 0.67 s for the same build minutes apart. Battery saver does the same
thing more quietly. Wake **and unlock** the device, and check `dumpsys power | grep mWakefulness`
says `Awake` rather than trusting that a `KEYCODE_WAKEUP` did it.

- **Cold start is 1.2–2.2 s to a screen of results**, as the system measures it — `reportFullyDrawn`
  fires on the first list, so `Displayed` is the spinner and `Fully drawn` is the real thing. Of
  that, opening the engine is 0.3–1.2 s, inserting 5,180 documents is 220–305 ms, and the two
  index builds are 79–107 ms together. **Opening the engine is the largest and by far the most
  variable part** — it is a 48 MB shared library being mapped, and it dominates a cold start far
  more than the seed does.
- **A warm start is 1.4–1.5 s**, of which the database is 440–490 ms. Nothing is re-seeded: the
  place count is read back and matches.
- **A settled pan costs 33 ms at the median and 75 ms at the worst**, over 40 pans. Asking for
  the whole island — every one of the 5,180 documents returned, paged over `getMore` and parsed
  into domain objects — is 51–75 ms. That work is off the main thread, and the canvas keeps
  drawing from the live camera while it runs, so a pan never waits on it.
- **Minify it or the numbers change.** The debug build is 2–3× slower on exactly these paths:
  950 ms to insert against 250 ms, and a 5,180-document pan at 136 ms against 63 ms. A
  measurement from a debug build is not a measurement of the application.
- **The database measures 10.3 MiB** on the device: 1.5 MB of documents, 0.7 MB across four
  indexes, and an 8 MiB journal — within a rounding error of what the emulator reported.
- **All four states of the location permission behave**, each driven from a wiped install.
  Granted, FusedLocationProvider answers and `$geoNear` measures from where the phone actually
  is — the pipeline screen prints the coordinates it was handed. Refused, the screen says
  "No location — measured from Dublin" and returns Henry St and O'Connell St at 19–34 m,
  ascending. Refused twice, the permission goes `USER_FIXED` and the button opens this
  application's page in system settings, which is the only way back. Revoked while running, the
  platform kills the process; see below.
- **The fix is not always prompt.** `getCurrentLocation` answered within a few seconds on some
  attempts and not at all on others, twice — on debug and release alike, so it is not R8. The call
  has no bound of its own: the `CurrentLocationRequest` its priority-only overload builds leaves
  `durationMillis` at `Long.MAX_VALUE`, so a provider that never calls back left the screen on
  "Finding you…" for the life of the process. It now waits ten seconds, cancels the request, and
  says "Gave up waiting for a location — measured from Dublin. Tap to try again." — told apart
  from a refusal, because a refusal is answered by granting the permission and a silence by asking
  again. Nothing is held up while it waits: the list is already answering from Dublin.
- **One `$geoWithin` returns all 5,180**, and plotting them draws a recognisable Ireland.
- **Killed with the engine open** — revoking a runtime permission makes the platform kill the
  process, which is a real SIGKILL mid-flight — it reopened with exactly 5,180 places, no repair
  and no reseed, in 466 ms.
- **Killed 0.4 s into seeding**, before any marker was written, it dropped the partial collection
  on relaunch and came back with exactly 5,180. That one is still an emulator result; the phone
  was interrupted with the database open rather than mid-seed.
- **R8 keeps what it must.** The release build seeds, queries, renders `Document.toJson` on the
  pipeline screen and reads the licence assets, on the library's `consumer-rules.pro` alone —
  this application adds no rule of its own.

### Still unverified

- **Nothing has run on a small or nearly full volume.** The free-disk floor was only ever
  observed passing: `getAllocatableBytes` answered 151,694,327,808 bytes on a 460 GB partition
  against the 64 MiB asked for, so the check is nowhere near its boundary and the refusal path
  has not been exercised on a device.
- **The device was in one place.** `$geoNear` from a real fix was measured in Dublin, so a fix
  outside the seed's bounding box has not been seen.
- **The ten-second budget has not been seen expiring on a device.** The hang it exists for was,
  twice, before there was one. What replaced it is proven on the JVM against a provider that never
  calls back — and the test was watched failing with the timeout taken out — but no phone has yet
  been observed reaching the end of the budget and saying so.
- **32-bit and Android 9–13 remain untested.** One arm64 phone on API 36 is one phone.

## Layout

```
data/                       plain Kotlin: no Android, no embedded-mongodb
  MongoSeam.kt              the one interface the engine is reached through
  PlaceRepository.kt        the three questions, and the command that answered each
  model/                    Coordinates, Metres, Viewport, Place, PlaceCategory
  query/                    every pipeline, built as org.bson.Document
  parse/                    replies into domain types, at one boundary
  seed/                     the gzipped BSON stream, and what puts it in the database
  geo/                      the camera and the projection the canvas draws through
  finder/                   the two screens' state, as StateFlows

app/                        the Android shell
  engine/                   EmbeddedMongoSeam -- the whole of the native contact
  location/                 FusedLocationProvider, a budget on waiting, and Dublin as the fallback
  ui/                       Compose: list, map, pipeline, about
  assets/places/            the seed, its attribution, and the licence texts it must ship with
                            (named .gzip, not .gz -- see "Things worth knowing")
```

## The seam

Every call into MongoDB goes through `MongoSeam`, which is two methods wide:

```kotlin
interface MongoSeam {
    suspend fun command(command: Document): Document
    fun documents(command: Document): Flow<Document>
}
```

`:data` sits entirely above it and depends on `org.mongodb:bson` and coroutines and nothing else,
so the pipelines, the parsing, the seeding and the screen state are all tested on the JVM against
a scripted fake — the same shape the library tests its own cursor paging with. `:app` supplies the
one implementation that talks to `EmbeddedMongo`, and it is a dozen lines of delegation.

That is not an abstraction added for its own sake. It is what let this application be written and
tested while the engine could not be linked, and it is what makes the engine swappable when the
release lands: `EmbeddedMongoSeam` forwards commands and `EmbeddedMongoOpener` starts the engine,
and they are the only two files that name `EmbeddedMongo` at all. `CoffeeDatabase` holds the
lifecycle around them and no engine type, which is what makes that lifecycle testable.

## Depending on the library

There is no Maven publication yet, so `settings.gradle.kts` consumes the module as an **included
build**:

```kotlin
includeBuild(libraryDir) {
    dependencySubstitution {
        substitute(module("io.github.jeroenvervaeke:embedded-mongodb"))
            .using(project(":embedded-mongodb"))
    }
}
```

An included build rather than a copied module or a checked-in AAR: the library's sources stay in
their own repository, `:app` sees whatever is checked out there, and there is no copy to go stale.
A copied `.aar` would have to be refreshed by hand every time the library changed, and copying the
module in would drag its `buildSrc` and its version catalog along with it.

The path defaults to `../embedded-mongo/android` and is the `embeddedMongoAndroidDir` Gradle
property, so the two repositories do not have to be siblings. If it is not there, the build says
so and `:data` and its tests still run — they do not depend on the library at all.

Both builds are pinned to the same AGP and Kotlin, because a composite build compiles them with
one of each. The AndroidX versions are pinned to what AGP 8.13.2 and `compileSdk` 36 accept;
anything newer demands AGP 9.1.

## Things worth knowing

**`minSdk` is 28, not the library's 24.** The library's floor is where its prebuilt engine
libraries are compiled against bionic, and that is a compile-time claim: it has not been run on a
device below 9.0, and the engine is known to crash on earlier ones. 28 is where this application
is prepared to say it works. Lowering it belongs with a device that proves it.

**Backup is off.** `android:allowBackup="false"`, which the library requires: a WiredTiger
directory restored onto another device, or onto another build of the application, is a corrupt
database rather than a migrated one. From API 31 that attribute only covers cloud backup, so
`data_extraction_rules.xml` excludes the directory from device-to-device transfer as well.

**The free-disk floor is lowered to 64 MiB.** MongoDB will not start an index build with less
than 500 MB free, and this application's cold start is a bulk insert followed by two index
builds — so at the default a phone with 400 MB free could open the database and never finish
seeding it. The library keeps 500 MB for every caller and lets one that knows better say so:
an Ireland-scale directory here is 10.3 MiB with its journal, measured on the phone, and 64 MiB
is roughly six times that. It is deliberately not lower. Nothing stops a build that runs out part-way —
WiredTiger answers a genuinely full disk by aborting the process, with nothing to catch — so the
floor is the only warning there is and the margin is the whole of the protection. Naming it also
lowers the library's pre-open check from 256 MiB to match.

**The engine is opened in an application-scoped coroutine, not the caller's.** The first ask
comes from a `ViewModel`, and leaving a screen while the engine is still starting is an ordinary
thing to do. Tied to that scope, the cancellation throws away an engine that came up anyway and
the next screen pays for a second cold start; the library closes it rather than stranding it, but
the work is still lost. Held by the application, the open finishes and the next screen is handed
the result. That scope is a `SupervisorJob`, because a failed open must not take the scope down
with it and leave the application unable to try again.

**The seed asset is `ireland.bson.gzip`, not `.gz`.** AGP's asset merger silently gunzips any
asset whose extension is exactly `gz` and packages it under the name without the extension — so
the shipped file would be `places/ireland.bson`, decompressed, and every launch would fail on a
`FileNotFoundException` for a name that is not there. `ShippedAssetsTest` reads what the merger
actually produced rather than what the source tree holds, which is the only place that is
visible.

**One APK per ABI.** The engine is 46 MB per architecture and is stored uncompressed, which is
right for the device — an uncompressed library is mapped rather than unpacked — but a universal
APK would hold both engines and make every arm64 phone carry 46 MB of x86_64 it can never run.
`splits { abi { … } }` builds one APK per ABI; a release would use an app bundle for the same
reason. Split, a release APK measures 59 MB: engine 46 MB, `libc++_shared` 8.8 MB, and 2.2 MB of
dex after R8. That `include` list is also what stops a 32-bit split ever being produced, since
MongoDB has no 32-bit build.

**The application times itself.** Every finished query and every phase of a start-up is one
`Log.i` line under the `CoffeeTimings` tag, so `adb logcat -s CoffeeTimings` is the whole
measuring apparatus and the numbers above can be re-taken on any device. The most recent one is
also on screen — under the map, and beside each command on the pipeline screen — because a pan
latency is worth seeing next to the pan. `reportFullyDrawn` covers the end a log line cannot:
process start and dex loading, which the system measures for itself and prints as `Fully drawn`.

**Places are counted with `$count`, not `{count: …}`.** The metadata count is the one operation
this project has measured going wrong after an unclean shutdown — and Android killing the process
mid-seed is exactly that. Seeding compares its marker against this number, so it walks the
collection instead.

**`:data` is a plain JVM module, so Android Lint never checks it for platform API levels.** Its
tests run on a JDK where everything exists. Anything it calls has to exist on API 28 — which
`InputStream.readNBytes` does not (it is API 33), which is why `BsonDocuments` fills its buffer
by hand.

**Nothing touches the engine on the main thread.** The suspending API dispatches onto the
library's own database thread, and the blocking one throws if it is called from the main thread.

**`$text` and `$geoNear` cannot be combined.** Both have to lead a pipeline, and `$geoNear`'s
`query` option will not take a `$text`. So search matches on the text index and then measures
distance itself, with a haversine written in the engine's own `$sin`, `$asin` and
`$degreesToRadians` — on the same sphere `$geoNear` measures on, so the two are comparable. The
test for it evaluates the expression and checks the answer against distances that are a matter of
record, rather than pinning the shape of the BSON.

## Building

```sh
export JAVA_HOME=/path/to/jdk21          # Android Studio's bundled runtime qualifies
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew :data:test                     # no SDK needed for this one
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug             # needs the library's engine to link
```

**R8 needs no rule from this application.** `app/proguard-rules.pro` is a comment and nothing
else. Everything a minified build needs — the JNI entry points, the BSON codecs, and a `-dontwarn`
for the SLF4J backend `org.mongodb:bson` carries and nothing here puts on the classpath — comes
from the library's own `consumer-rules.pro`. That last one used to be duplicated here, because
`bson` is an `api` dependency of the library and every consumer that minifies inherits the
dangling reference; the library has since taken it over, so this build now requires a library
checkout at `808276e` or later. Against an older one the release build fails outright on
`Missing class org.slf4j.Logger`.

[library]: https://github.com/jeroenvervaeke/embedded-mongo
