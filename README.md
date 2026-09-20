![MP4 to MP3](docs/banner.png)

# MP4 to MP3

Pick videos, or share them to the app: each becomes a 320 kbps MP3 (LAME) in
Downloads, ready to share. One screen, whole batches, and it keeps going in the
background. No network permission, no ads, no account.

## Key points

- Videos are chosen with the system photo picker or the file browser; the app never
  asks for storage permission. Videos shared from another app are converted too.
- Every format the phone can play works: MP4, MKV, WebM, 3GP…
- Output is 320 kbps CBR. The sample rate is kept when it is 32, 44.1 or 48 kHz; mono
  stays mono, more than two channels are folded down to mono.
- When the batch is done, each file in the list has a **Share** button.
- Leaving the app or locking the screen does not interrupt a batch.
- A failed or cancelled job leaves no truncated MP3 behind.
- Needs Android 10 or newer; `arm64-v8a`, `armeabi-v7a` or `x86_64`.

More detail: [docs/NOTES.md](docs/NOTES.md).

## Install

From the [F-Droid repo](https://funkypitt.github.io/fdroid-repo/): add
`https://funkypitt.github.io/fdroid-repo/repo` in F-Droid.

## Build

```sh
./gradlew assembleRelease
```

Needs the NDK (27.1.12297006) and CMake 3.22.1. Nothing is downloaded at build
time: the LAME sources are vendored in this repository.

## Third-party code

`app/src/main/cpp/lame/` contains **LAME 3.100**, unmodified upstream sources
(`sha256 ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`),
licensed **LGPL-2.1-or-later**. See `app/src/main/cpp/lame/COPYING`.

LAME's build system normally generates a `config.h`; since Android/NDK is a
fixed target, `app/src/main/cpp/config.h` is a hand-written equivalent. The LAME
sources themselves are untouched.

## Licence

GPL-3.0-only. See `LICENSE`.

## Crédits / Credits

© 2026 Pierre Gallaz. Développé avec [Claude Code](https://claude.com/claude-code) (Anthropic).
Licence GPL-3.0-only, voir `LICENSE`.

© 2026 Pierre Gallaz. Developed with [Claude Code](https://claude.com/claude-code) (Anthropic).
GPL-3.0-only licence, see `LICENSE`.

## Captures d'écran

<img src="docs/screenshot-1.png" width="30%">
