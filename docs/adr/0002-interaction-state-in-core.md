# Keep ScopeX interaction state in core

ScopeX interaction state lives in `scopex-core` as pure state because recording mode, input cache panel behavior, active source locking, frozen scope, and cache highlight transitions are product rules shared by glasses, Android, and future remote inputs. Platform modules should translate hardware events into canonical actions and execute side effects, not own divergent interaction state machines.
