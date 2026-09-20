# MP4 to MP3 — notes

Reference material moved out of the README.

## How it works

1. Videos are chosen with the **system photo picker** (or the file browser),
   which grants access to just those files — the app never asks for storage
   permission.
2. The audio track is demuxed and decoded with the platform decoder
   (`MediaExtractor` + `MediaCodec`), so every format the phone can play is
   supported: MP4/H.264 with AAC, MKV, WebM, 3GP, and so on.
3. PCM goes straight into **LAME** (compiled in, no temporary files) at
   320 kbps CBR. The source sample rate is kept when it is 32/44.1/48 kHz;
   anything else is resampled by LAME to a rate that can carry 320 kbps.
   Mono stays mono; more than two channels are folded down to mono rather than
   guessing at an unknown channel layout.
4. The result is written through `MediaStore` into `Downloads`, as a *pending*
   entry that is only published once the conversion succeeds — a failed or
   cancelled job never leaves a truncated MP3 behind.
5. The batch runs in a **foreground service** with a wake lock, so leaving the
   app or locking the screen does not interrupt it.

Videos shared to the app from another app's share sheet are converted too.
