# Nothing of this application's own needs keeping: it has no reflection, no serialisation by name
# and no JNI. The rules that do matter arrive from the library's consumer-rules.pro, which keeps
# the NativeBridge entry points R8 would rename into an UnsatisfiedLinkError and the BSON codecs
# it would otherwise strip as unreachable.

# org.mongodb:bson ships org.bson.diagnostics.SLF4JLogger, which references org.slf4j and is only
# used when SLF4J is on the classpath. Nothing here puts it there -- the library depends on bson
# alone -- so the class is unreachable, but R8 still refuses to finish with a dangling reference:
#
#   Missing class org.slf4j.Logger (referenced from: org.bson.diagnostics.SLF4JLogger.delegate)
#
# This is not app-specific. `bson` is an api dependency of embedded-mongodb, so any consumer that
# turns on minification hits it; the library's own consumer-rules.pro would be the better home.
-dontwarn org.slf4j.**
