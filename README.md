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

## What is built, and what is not

The library's published engine prebuilts predate a change to its native inputs, so its freshness
gate refuses to link an AAR from this checkout and a release is being published. Everything that
does not need the native library is written and verified; everything that does is written and
has never been run.

| | |
| --- | --- |
| `./gradlew :data:test` | **runs, green.** 138 tests, no Android SDK needed |
| `./gradlew :app:testDebugUnitTest` | **runs, green.** 31 tests — the real seed, the packaged assets, and the engine's lifecycle against a fake opener |
| `./gradlew :app:compileDebugKotlin` | **compiles**, against the real library classes |
| `./gradlew :app:compileReleaseKotlin` | **compiles** |
| `./gradlew :app:assembleDebug`, `:app:lintDebug` | **blocked**: both need `cargoJniLibs` |

No device or emulator has run any of this. The seeding, the engine seam and every screen are
written and unexercised — no engine has ever answered one of these pipelines.

What *is* checked against reality rather than against a fake: `ShippedSeedTest` reads the seed
through the same stream reader and parser the application uses, and asserts that all 5,180
documents parse, that every place is inside the extent the seed was extracted with, and that
every category in it is one the application knows. `ShippedAssetsTest` goes one step further and
reads what AGP's asset merger actually produced, because the packaged assets are not the same
files as the source tree's.

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
  location/                 FusedLocationProvider, with Dublin as the fallback
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
an Ireland-scale directory here is about 10.25 MiB with its journal, and 64 MiB is roughly six
times that. It is deliberately not lower. Nothing stops a build that runs out part-way —
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

**One APK per ABI.** The engine is about 48 MB per architecture and is stored uncompressed,
which is right for the device — an uncompressed library is mapped rather than unpacked — but a
universal APK would hold both engines, land near 97 MB against Play's 100 MB ceiling, and make
every arm64 phone carry 48 MB of x86_64 it can never run. `splits { abi { … } }` builds one APK
per ABI; a release would use an app bundle for the same reason. That `include` list is also what
stops a 32-bit split ever being produced, since MongoDB has no 32-bit build.

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

[library]: https://github.com/jeroenvervaeke/embedded-mongo
