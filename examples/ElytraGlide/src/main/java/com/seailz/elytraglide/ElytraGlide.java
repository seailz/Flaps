package com.seailz.elytraglide;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;


import com.seailz.flaps.Flaps;
import com.seailz.flaps.FlapsEffect;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ElytraGlide extends JavaPlugin {

    private static final float MAX_ROLL_DEG = 70.0f;      // clamp roll magnitude
    private static final float ROLL_PER_YAW_DEG = 12.0f;  // roll degrees per degree yaw delta per tick (strong response)
    private static final int ROLL_TRANSITION_TICKS = 5;   // smoothing ticks on the wire
    private static final float YAW_SMOOTH_ALPHA = 0.10f;  // low-pass factor for yaw rate (higher = snappier)
    private static final float YAW_DEADZONE_DEG = 0.75f;  // ignore tiny yaw jitter

    private ProtocolManager protocolManager;
    private Flaps bus;

    // Track last yaw per player to compute yaw rate
    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    // Smoothed yaw rate per player to reduce jitter
    private final Map<UUID, Float> smoothedYawRate = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        bus = Flaps.getInstance();
        System.out.println(Flaps.getInstance());

        // Drive roll based on yaw rate during elytra flight
        Bukkit.getScheduler().runTaskTimer(this, this::updateRollFromYaw, 1L, 1L);
    }

    @Override
    public void onDisable() {
        lastYaw.clear();
        if (bus != null) bus.stop();
        if (protocolManager != null) protocolManager.removePacketListeners(this);
    }

    /** Compute yaw rate per player and feed it into the roll effect. */
    private void updateRollFromYaw() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            float currentYaw = normalizeYaw(player.getLocation());
            float previousYaw = lastYaw.getOrDefault(id, currentYaw);
            float yawDelta = wrapYawDelta(currentYaw - previousYaw);
            lastYaw.put(id, currentYaw);

            // Low-pass filter the yaw rate to reduce jank/lag spikes
            float prevSmoothed = smoothedYawRate.getOrDefault(id, yawDelta);
            float smoothed = lerp(prevSmoothed, yawDelta, YAW_SMOOTH_ALPHA);
            smoothedYawRate.put(id, smoothed);

            // Deadzone to ignore tiny jitter
            if (Math.abs(smoothed) < YAW_DEADZONE_DEG) {
                smoothed = 0f;
            }

            if (!player.isGliding()) {
                // Disable roll when not gliding and zero out arg0
                bus.player(player)
                        .disable(FlapsEffect.ROLL)
                        .arg0Signed(0f)
                        .transitionTicks(ROLL_TRANSITION_TICKS)
                        .commit();
                continue;
            }

            // Convert yaw rate to roll angle, clamp, then normalize to [-1, 1]
            float targetRollDeg = clamp(smoothed * ROLL_PER_YAW_DEG, -MAX_ROLL_DEG, MAX_ROLL_DEG);
            float norm = -targetRollDeg / MAX_ROLL_DEG;
            // Invert
            norm = clamp(norm, -1f, 1f);

            bus.player(player)
                    .enable(FlapsEffect.ROLL)
                    .arg0Signed(norm)
                    .transitionTicks(ROLL_TRANSITION_TICKS)
                    .commit();
        }
    }

    private static float normalizeYaw(Location loc) {
        float yaw = loc.getYaw();
        // Normalize to [-180, 180)
        yaw = ((yaw + 180f) % 360f + 360f) % 360f - 180f;
        return yaw;
    }

    private static float wrapYawDelta(float delta) {
        // Wrap to [-180, 180)
        delta = ((delta + 180f) % 360f + 360f) % 360f - 180f;
        return delta;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
