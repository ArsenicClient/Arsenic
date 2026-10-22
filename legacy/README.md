# legacy

The 1.8.9 client's sources (`java/`) and shaders (`shaders/`) as of the `main` commit in `MERGE_BASE`, the last commit
of `main` merged into this branch. They are kept for reference when porting and are not compiled. Diff them against a
newer `main` to see what has changed since.

Not ported to 26.3:

- The injector (`arsenic.inject`, `arsenic.runtime`): Fabric loads the client as a mod, with mixins.
- Recorder, RotationView and the capture tools.
- The sword block-hit render animation, and theming of vanilla screens.
- Chams and the old GL shader effects (blur, bloom, animated backgrounds).
