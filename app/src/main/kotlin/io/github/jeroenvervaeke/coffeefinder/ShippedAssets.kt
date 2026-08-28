package io.github.jeroenvervaeke.coffeefinder

/**
 * The gzipped BSON seed, built by the library's `scripts/build-places-seed` and shipped unchanged.
 *
 * `.gzip`, not the `.gz` that script writes, and the extension is the whole reason for this
 * constant existing: AGP's asset merger gunzips any asset whose extension is exactly `gz` and
 * packages it under the name without it. The application would then ask for a file that is not
 * there — and if it found it, the bytes would no longer be compressed.
 *
 * It is silent, it only happens in the packaged application, and no test that reads the source
 * tree can see it. `ShippedAssetsTest` reads what AGP actually produced.
 */
const val SEED_ASSET = "places/ireland.bson.gzip"
