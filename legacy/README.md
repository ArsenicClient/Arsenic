# legacy

The 1.8.9 client's sources (`java/`) and shaders (`shaders/`) as of the `main` commit in `MERGE_BASE`, the last commit
of `main` merged into this branch. They are kept for reference when porting and are not compiled. Diff them against a
newer `main` to see what has changed since.

Not ported to 26.3:

- The injector (`arsenic.inject`, `arsenic.runtime`): Fabric loads the client as a mod, with mixins.
- Recorder, RotationView and the capture tools.
- The sword block-hit render animation.
- The Kawase blur/bloom shaders themselves: blur is rebuilt on the game's own blur (`BlurBackdrop`), bloom is drawn
  as offset rings (`PostProcessing`), chams as a render type of their own (`ChamsRenderer`).
