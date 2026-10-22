# Ghost Context Lazy Resolution Design

## Goal

Start playback without resolving every location in a large Ghost Context while preserving its complete logical order and all queue controls.

## Current failure

`MainActivity.syncQueueToSession` resolves every queue item before it calls `PlaybackSessionClient.loadQueue`. A 7,036-track Ghost Context therefore performs thousands of SAF/Room lookups before Media3 receives even the selected track.

## Invariants

- `PlaybackQueueController` remains the canonical, complete ordered Ghost Context.
- A Ghost Context never becomes a smaller User Queue merely to reduce work.
- Current selection, Next, Previous, repeat, and domain shuffle remain defined by `PlaybackQueueController`.
- A manual add or Play Next retains the existing Ghost-to-User-Queue takeover behavior.
- No production catalog, media file, metadata, or Android audio-volume setting is modified.

## Design

Introduce a playback-window projection between the domain queue and `PlaybackSessionClient`.

1. The projection receives the full ordered domain queue, the current entry, and a resolver.
2. It resolves a bounded window: current item, one previous item, and two following items. It preserves domain ordering in the projected items and retains the mapping back to each entry id.
3. Media3 is loaded with this projected window so selected playback can begin after only the current-location lookup (and a small bounded number of adjacent lookups), rather than after all 7,036 lookups.
4. Before an in-window transition reaches its final look-ahead item, the next bounded window is resolved on the background executor and replaces or extends the Media3 projection while retaining the domain current entry and playback position.
5. A domain command whose selected entry is absent from the active projection resolves a new window centered on that entry, then loads it. Thus Next, Previous, shuffled targets, and direct queue selections remain correct even when they jump outside the existing Media3 window.
6. Queue mutations invalidate the active projection and produce a new window from the updated domain queue. The full domain queue continues to be persisted unchanged.

## Error handling

An unresolved or inaccessible location is excluded only from the Media3 window, never from the domain queue. If the selected entry has no accessible location, playback reports the existing error state and leaves domain queue state intact.

## Testing

- Projection resolves a bounded set around the selected queue entry.
- Projection preserves Ghost order and stable entry ids.
- Selecting an entry outside the current window requests a new centered window.
- Shuffle, repeat, Previous/Next, and Ghost-to-User takeover continue to use the full domain queue.
- Existing `PlaybackQueueTest` coverage remains valid.

## Verification status and limitation

The local Gradle test environment runs with Android Studio JBR and process-local `ANDROID_HOME`; the focused playback tests and full JVM unit-test suite pass. A connected-device benchmark still requires a separate debug package and an already-granted debug SAF source. It must not use, alter, or reset the production package, production Room data, production SAF grants, media files, or audio-volume setting.
