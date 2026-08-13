# Patches

## zos-pthread-tls.patch — READ THIS BEFORE RELYING ON THIS PORT

**This patch changes libsodium's random number generator. It has not been
reviewed by anyone other than its author, and it should be before this port is
trusted for anything that matters.**

### What it changes

`randombytes_internal_random.c` keeps its ChaCha20 RNG state in a single
thread-local variable:

```c
static TLS InternalRandom stream = { ... };
```

The z/OS compiler supports neither `_Thread_local` nor `__thread`, so
libsodium's own macro cascade falls through to its empty `TLS` fallback. That
fallback compiles, and it makes `stream` a single shared object — so concurrent
`randombytes_*` calls race on the key, the nonce and the output buffer. In a
random number generator that is a correctness bug with security consequences,
not a performance one, and paramiko (the reason this port exists) is commonly
used from threads.

The patch keeps the state per-thread using POSIX thread-specific data, which
z/OS does support. All 25 existing `stream.field`, `&stream` and `sizeof
stream` references are left untouched by redirecting them through a macro, so
the change is two hunks in one file. It is guarded on `__MVS__`; every other
platform keeps upstream's cascade exactly as it was.

### Two z/OS details it depends on

`<pthread.h>` here declares *both* a POSIX `void *pthread_getspecific(pthread_key_t)`
and a legacy `int pthread_getspecific(pthread_key_t, void **)`, selected by
feature-test macros. This compilation gets the legacy form, so the patch uses
it. Getting this wrong fails with "too few arguments to function call", which
says nothing about portability.

`pthread_t` is not declared at all without `_OPEN_THREADS`, which is why the
check compiles with `-D_OPEN_THREADS=3`.

### What has been verified

- libsodium builds clean and produces a 1.1 MB `libsodium.a`.
- Known answers match published vectors: SHA-256 of `61 62 63`, the RFC 7539
  all-zero ChaCha20 block, Ed25519 sign/verify including tampered-signature
  rejection, secretbox round trip.
- Concurrency: 8 threads x 2000 draws x 3 rounds produced no all-zero blocks
  and no duplicate blocks, with threads created and destroyed repeatedly so the
  key destructor runs.

### What has *not* been verified

Upstream's `make check` does not pass here and cannot be used as evidence
either way — its tests hash C string literals, which are EBCDIC on z/OS, and
compare against `.exp` files generated from ASCII inputs. It reports 5 failures
and 31 errors against a library computing everything correctly. The port's own
check uses explicit byte arrays for this reason.

Empirical concurrency testing is not proof of thread safety, and this is
cryptographic code. Upstream review, or at minimum a second reader, is the
right bar before this is depended on.
