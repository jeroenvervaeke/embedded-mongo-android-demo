# Coffee Offline

[![MongoDB Community 9.0.0-alpha0](https://img.shields.io/badge/MongoDB_Community-9.0.0--alpha0-47A248?logo=mongodb&logoColor=white)](https://github.com/jeroenvervaeke/embedded-mongo)
[![embedded-mongodb](https://img.shields.io/badge/engine-embedded--mongodb-47A248)](https://github.com/jeroenvervaeke/embedded-mongo)
[![Android 9.0+](https://img.shields.io/badge/Android-9.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

**MongoDB, running inside an Android application. With the aeroplane mode switch on.**

Every coffee place on the island of Ireland, all 5,180 of them, searchable by name, sorted by
distance from where you are standing, and plotted on a map. No server. No network call. No API
key. No map SDK.

The database is [embedded-mongodb][library]: MongoDB's own query engine and WiredTiger storage,
compiled into the application process. Not a MongoDB-compatible reimplementation with a
MongoDB-shaped name on it: MongoDB's actual server code, answering `$geoNear`, `$text` and
`$geoWithin` out of a directory in the app's private storage.

> [!NOTE]
> An independent demonstration of an experimental engine. Not supported by, endorsed by, or
> affiliated with MongoDB, Inc.

## The same MongoDB you already write

This is the whole of the "what is nearest" query. It is a `MongoCollection`, an aggregation
pipeline, and `org.bson.Document`, the API every MongoDB developer already has in their hands:

```kotlin
val pipeline = listOf(
    Document("\$geoNear", Document("near", geoJsonPoint(from))
        .append("distanceField", "distance")
        .append("spherical", true)),
    Document("\$limit", limit),
)

val query = places.aggregate(pipeline)
val nearby = query.asFlow(Document::toNearbyPlace).toList()
```

A real `$geoNear`, planned and executed by MongoDB against a real `2dsphere` index it built
itself. Point the same pipeline at Atlas and it runs unchanged.

## Why an embedded engine changes things

- 🍃 **The interface you already know.** `MongoCollection`, `aggregate`, cursors, `org.bson`,
  `Document`. No new query language, no ORM, no dialect to learn, no second data model to keep
  in step with the server one.
- ✈️ **Offline by construction.** There is nothing to be offline *from*. The engine is in the
  process; the data is in a local directory. Aeroplane mode changes nothing.
- 📦 **No server, no ports, no connection string.** Deployment is a directory, the way SQLite's
  is a file. Nothing to install, provision, or keep running beside the app.
- 🗺️ **The whole query surface, not a subset.** `$geoNear` over a `2dsphere` index, `$text`
  over a text index, `$geoWithin` with a polygon, and a haversine written in the engine's own
  `$sin`, `$asin` and `$degreesToRadians`, all planned and executed by MongoDB.
- 🧪 **A data layer you can test on a laptop.** The whole of it is plain Kotlin against the
  library's Android-free core module: 140 tests, no emulator, no compiled engine.
- 🌍 **One engine across ecosystems.** Rust and Python today, Android here, the same C ABI
  underneath. The queries move between them because they are MongoDB's queries.

## Better than SQLite for this

SQLite is the reflex answer to on-device storage, and it is a fine database. It is the wrong
shape for this data, and for any application whose data also lives on a server.

- 📄 **The document model, all the way down.** A coffee place *is* a document: a name, a
  category, a GeoJSON point, and an address whose every field is optional: street, locality,
  postcode, region, any of them absent. In SQLite that is a `places` table plus a ladder of
  nullable columns, or a join to an addresses table, and either way the place is decomposed on
  the way in and reassembled on the way out, by hand or by an ORM. Here it is stored as the
  shape it already is, and read back as that shape.
- 🔁 **One data model, phone and server.** The alternative is SQL on the device and MongoDB
  behind the API: two schemas, two query languages, and a translation layer between them that
  every new field has to pass through. The same document, the same index and the same pipeline
  work in both places.
- 🌱 **The seed is the server's own BSON.** `ireland.bson.gzip` is documents, inserted as they
  are. A SQLite build of this needs an ETL step that flattens them into rows first, and that
  step has to be maintained in step with the schema on both ends.
- 🗺️ **Geospatial without a bolted-on extension.** SQLite's R\*Tree indexes bounding boxes, so
  "nearest, in order" is a box query followed by a haversine you write and a sort you pay for,
  or SpatiaLite, a second dependency. `$geoNear` walks a `2dsphere` index outwards from the
  point and returns the 50 the list asks for, already ordered, having read 50 rather than
  5,180.
- 🔤 **Text search on the collection itself.** FTS5 is good, but it is a separate virtual table
  kept in step with the real one by triggers. A `$text` index is an index on the documents.
- 🧬 **New fields need no migration.** Adding a field to a document is adding a field. There is
  no `ALTER TABLE` ladder to write, version and run against installs that skipped three
  releases.

The cost is honest and worth stating: SQLite is already in Android and costs nothing to ship,
while the engine adds 46 MiB per ABI, and it is decades of production hardening against an
experimental build. That is the trade being made here.

## The screen that proves it

Anyone can claim an engine. So every result carries the exact command that produced it, and a
pipeline screen prints it:

```json
{ "aggregate": "places",
  "pipeline": [ { "$geoNear": { "near": { "type": "Point", "coordinates": [-6.26, 53.35] },
                                "distanceField": "distance", "spherical": true } },
                { "$limit": 50 } ],
  "cursor": {} }
```

That document is not a description of the query. It is the one the driver sent, travelling back
with its own results, so what is on screen cannot drift from what the engine ran. `buildInfo`
reports `modules: ["embedded"]`, which no real `mongod` ever does.

## The map is the data

The map is a `Canvas`, drawn from the coordinates the query returned. There are no tiles under
it and no map SDK in the build. Ireland is recognisable because five thousand coffee places are
enough to draw its towns and its coast road: the shape *is* the data. One `$geoWithin` with a
polygon returns all 5,180 in 51–75 ms.

## On a real phone

Measured on a Galaxy S23 Ultra, release build, five runs each:

| | |
| --- | --- |
| Cold start, seeding 5,180 documents and building both indexes | **0.7–1.1 s** warm-cache, up to 3.6 s on the first launch after a reboot |
| Warm start, database only | **0.3–0.6 s**. Nothing is re-seeded |
| The 50 nearest, `$geoNear` | **15–38 ms** |
| Every place on the island, one query | **71–114 ms** |
| Database on disk | **10.3 MiB**, of which 1.5 MB documents, 0.7 MB indexes, 8 MiB journal |
| Release APK | **59 MiB** per ABI, of which the engine is 46 MiB |

Killed mid-flight with the engine open, it reopens with exactly 5,180 places in 466 ms, with no
repair and no reseed.

## Build it

```sh
export JAVA_HOME=/path/to/jdk21          # Android Studio's bundled runtime qualifies
export ANDROID_HOME=/path/to/Android/Sdk

git clone https://github.com/jeroenvervaeke/embedded-mongo.git    # sibling checkout
./gradlew :app:assembleDebug
```

The library is consumed as a Gradle included build from `../embedded-mongo/android`; set the
`embeddedMongoAndroidDir` property if the two repositories are not siblings. `./gradlew
:data:test` runs without either an emulator or a compiled engine.

[AGENTS.md](AGENTS.md) has the module layout, the build details, and everything learned the hard
way.

## Data, attribution and licences

Places come from the [Overture Maps Foundation](https://overturemaps.org), release
2026-08-19.0, accessed 2026-08-27. **The data has been modified**: filtered to four coffee
categories inside a bounding box around Ireland, and reshaped into the documents this
application stores.

© 2026 Foursquare Labs, Inc. All rights reserved.

Contributing datasets are covered by CDLA-Permissive-2.0, the Apache License 2.0 with the
Foursquare NOTICE, and CC0-1.0. **All four texts ship inside the application**, in
`app/src/main/assets/places/licenses/`, shown in full on the About screen, because naming a
licence does not satisfy it: CDLA-Permissive-2.0 section 2.1 and the NOTICE both require the
text to travel with the data. CC0-1.0 imposes no such condition and is shipped anyway, so that
every licence the data is under can be read inside the application.
`app/src/main/assets/places/ireland.attribution.txt` records the extraction and every
modification made to the data, and `licenses/SOURCES` records where each text was fetched from.

The seed itself is `app/src/main/assets/places/ireland.bson.gzip`: 5,180 documents of
concatenated BSON, gzipped to 454 KB, produced by the library's `scripts/build-places-seed`.

## This project is not a MongoDB product

Not supported by, endorsed by, or affiliated with MongoDB, Inc. No MongoDB logo or branding is
used here. It is an independent demonstration of an embedded build of the engine, which is
itself experimental and not production-ready.

[library]: https://github.com/jeroenvervaeke/embedded-mongo
