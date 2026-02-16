package com.seailz.flaps;

import com.seailz.flaps.utils.FlapsCodec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Per-player Flaps effect manager, allows enabling/disabling effects and setting parameters.
 */
public final class FlapsPlayerManager {
    private final Flaps bus;
    private final Player player;

    // “pending” ops
    private int pendingMaskOr = 0;
    private int pendingMaskAnd = 0xFF; // keep bits
    private Float pendingArg0 = null;
    private Boolean pendingArg0Counter = null;
    private Integer pendingTransitionTicks = null;

    FlapsPlayerManager(Flaps bus, Player player) {
        this.bus = bus;
        this.player = player;
    }

    public Player bukkit() {
        return player;
    }

    /**
     * Enables an effect for a player (turns the bit in the mask on).
     * @param effect The effect to enable.
     */
    @Contract(value = "_ -> this", mutates = "this")
    @CheckReturnValue
    public FlapsPlayerManager enable(@NotNull FlapsEffect effect) {
        pendingMaskOr |= effect.mask();
        return this;
    }

    /**
     * Disables an effect for a player (turns the bit in the mask off).
     * @param effect The effect to disable.
     */
    @Contract(value = "_ -> this", mutates = "this")
    @CheckReturnValue
    public FlapsPlayerManager disable(@NotNull FlapsEffect effect) {
        pendingMaskAnd &= ~effect.mask();
        return this;
    }

    /**
     * Replaces the bitwise mask that controls which effects are active completely.
     * <br>Not usually needed; prefer enable() and disable().
     * @param mask The new mask (0..255).
     * @see FlapsPlayerManager#enable
     */
    @CheckReturnValue
    public FlapsPlayerManager setMask(int mask) {
        if (mask < 0 || mask >= FlapsCodec.MASK_BASE) {
            throw new IllegalArgumentException("mask must be 0..255");
        }
        // Implement as clear-all then OR
        pendingMaskAnd = 0;
        pendingMaskOr = mask;
        return this;
    }

    /**
     * Sets the first argument (arg0) as normalized 0..1. <p>Pick either this or {@link #arg0Signed(float)} depending on whether you need a signed (ability to represent negative values) argument.
     * @param arg01 The new arg0 value.
     */
    @CheckReturnValue
    public FlapsPlayerManager arg0(float arg01) {
        pendingArg0 = FlapsCodec.clamp01(arg01);
        pendingArg0Counter = false;
        return this;
    }

    /**
     * Sets the first argument (arg0) as signed -1..+1. <p>Pick either this or {@link #arg0(float)} depending on whether you need a signed (ability to represent negative values) argument.
     * @param signed The new arg0 value.
     */
    @CheckReturnValue
    public FlapsPlayerManager arg0Signed(float signed) {
        float clamped = FlapsCodec.clamp(signed, -1f, 1f);
        float arg01 = (clamped * 0.5f) + 0.5f;
        pendingArg0 = arg01;
        pendingArg0Counter = false;
        return this;
    }

    /**
     * Sets arg0 to be driven by an internal counter (0..{@link FlapsCodec#ARG0_MAX}, wraps every tick).
     * <p>This is useful as a simple time source in the shader when you don't need an actual parameter.
     * <p>Calling {@link #arg0(float)} or {@link #arg0Signed(float)} disables counter mode.
     */
    @CheckReturnValue
    public FlapsPlayerManager arg0Counter() {
        pendingArg0 = null;
        pendingArg0Counter = true;
        return this;
    }

    /**
     * Allows setting a transition duration for arg0 changes.
     * <p>These are server controlled and so can cause lagging or some stuttering. Shaders cannot store state, so there is no solution to this.
     * @param ticks The duration in ticks (0 = immediate).
     */
    @CheckReturnValue
    public FlapsPlayerManager transitionTicks(int ticks) {
        if (ticks < 0) ticks = 0;
        pendingTransitionTicks = ticks;
        return this;
    }

    /**
     * Sends the pending changes to the player's client.
     */
    public void commit() {
        bus.apply(player, pendingMaskOr, pendingMaskAnd, pendingArg0, pendingTransitionTicks, pendingArg0Counter);

        // reset pending ops so the builder can be reused
        pendingMaskOr = 0;
        pendingMaskAnd = 0xFF;
        pendingArg0 = null;
        pendingArg0Counter = null;
        pendingTransitionTicks = null;
    }
}
