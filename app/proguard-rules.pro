# Nothing of this application's own needs keeping: it has no reflection, no serialisation by name
# and no JNI. The rules that do matter arrive from the library's consumer-rules.pro, which keeps
# the NativeBridge entry points R8 would rename into an UnsatisfiedLinkError and the BSON codecs
# it would otherwise strip as unreachable.
