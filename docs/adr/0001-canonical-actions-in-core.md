# Define canonical ScopeX actions in core

Canonical ScopeX actions live in the pure `scopex-core` boundary because click, hold, scroll, zoom, ASR control, cache-panel control, and source locking are product semantics, not Android injection details or glasses hardware details. Android, glasses, and future remote adapters should translate device events into the same action vocabulary instead of owning separate behavior.
