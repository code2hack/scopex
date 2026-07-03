# Core emits effect commands

`scopex-core` returns platform-neutral effect commands alongside new interaction state because ScopeX rules must stay pure and testable while Android, glasses, and remote adapters perform platform-specific work. Platforms should execute explicit commands such as start ASR, inject tap, show prompt, or open permission screen instead of inferring side effects from state changes.
