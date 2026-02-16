package com.seailz.flaps.utils.transition;

import java.util.UUID;

public class FlapsPlayerState {
    final UUID uuid;

    public int mask = 0;

    // We store arg0 as float 0..1 (normalized)
    public float arg0 = 0.5f;

    // If true, arg0 is driven by an internal counter (0..ARG0_MAX, wraps).
    public boolean arg0CounterEnabled = false;
    public int arg0CounterValue = 0;

    // Transition state for arg0
    public float startArg0 = 0.5f;
    public float targetArg0 = 0.5f;
    public long startTick = 0;
    public int durationTicks = 0;
    public boolean transitioning = false;

    // Cached packed timeOfDay last sent (optional)
    public long lastTimeOfDay = -1;

    public FlapsPlayerState(UUID uuid) {
        this.uuid = uuid;
    }

}
