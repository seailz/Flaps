package com.seailz.flaps;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.seailz.flaps.utils.FlapsCodec;
import com.seailz.flaps.utils.transition.FlapsEasing;
import com.seailz.flaps.utils.transition.FlapsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Handles global Flaps effect control and ticking.
 */
public final class Flaps {

    private static Flaps instance;

    private final Plugin plugin;
    private final ProtocolManager protocolManager;

    private final Map<UUID, FlapsPlayerState> states = new ConcurrentHashMap<>();

    private long tickCounter = 0;
    private int taskId = -1;

    // If true, we send every tick. If false, send only when value changes or when intercepting a native packet.
    private volatile boolean sendEveryTick;

    // Optional override for generating a custom timeOfDay per player each tick.
    private volatile BiFunction<Player, FlapsPlayerState, Long> customTimeProvider = null;

    /**
     * Makes an instance of Flaps, the main controller for effects.
     * @param plugin The plugin instance.
     * @param protocolManager An instance of ProtocolManager from ProtocolLib, can be obtained via <code>ProtocolLibrary.getProtocolManager()</code>.
     * @param sendEveryTick Whether or not to send packets every tick, even if the value hasn't changed. Setting this to false can reduce bandwidth but could lead to less smooth transitions for some effects. If set to false, packets are only sent when the value changes or when intercepting a packet that the server would have otherwise already sent.
     */
    protected Flaps(Plugin plugin, ProtocolManager protocolManager, boolean sendEveryTick) {
        instance = this;

        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.sendEveryTick = sendEveryTick;
    }

    /**
     * Whether or not to send packets every tick, even if the value hasn't changed.
     * <p>Setting this to false can reduce bandwidth but could lead to less smooth transitions for some effects. If set to false, packets are only sent when the value changes or when intercepting a packet that the server would have otherwise already sent.
     * <p>Default state: <b>true</b>
     */
    public Flaps sendEveryTick(boolean sendEveryTick) {
        this.sendEveryTick = sendEveryTick;
        return this;
    }

    public static Flaps getInstance() {
        return instance;
    }

    /**
     * Starts sending packets to players. Call in onEnable().
     */
    protected void start() {
        if (taskId != -1) return;

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            tickCounter++;

            for (Player p : Bukkit.getOnlinePlayers()) {
                FlapsPlayerState st = states.computeIfAbsent(p.getUniqueId(), FlapsPlayerState::new);

                // Allow custom provider to override timeOfDay packing
                BiFunction<Player, FlapsPlayerState, Long> provider = customTimeProvider;
                Long customTime = provider != null ? provider.apply(p, st) : null;

                // Advance arg0 transition if needed
                advance(st);

                // Pack and send
                long timeOfDay;
                if (customTime != null) {
                    timeOfDay = customTime;
                } else {
                    int arg0i;
                    if (st.arg0CounterEnabled) {
                        arg0i = st.arg0CounterValue;
                        st.arg0CounterValue = (st.arg0CounterValue + 1) % (FlapsCodec.ARG0_MAX + 1);
                    } else {
                        arg0i = FlapsCodec.arg0iFrom01(st.arg0);
                    }
                    timeOfDay = FlapsCodec.packTimeOfDay(st.mask, arg0i);
                }

                if (sendEveryTick || timeOfDay != st.lastTimeOfDay) {
                    sendTimePacket(p, timeOfDay);
                    st.lastTimeOfDay = timeOfDay;
                }
            }
        }, 1L, 1L);

        // Intercept native UPDATE_TIME packets to inject our custom timeOfDay
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.UPDATE_TIME
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player p = event.getPlayer();
                FlapsPlayerState st = states.computeIfAbsent(p.getUniqueId(), FlapsPlayerState::new);

                BiFunction<Player, FlapsPlayerState, Long> provider = customTimeProvider;
                Long customTime = provider != null ? provider.apply(p, st) : null;

                long timeOfDay = st.lastTimeOfDay >= 0
                        ? st.lastTimeOfDay
                        : (customTime != null
                        ? customTime
                        : FlapsCodec.packTimeOfDay(
                                st.mask,
                                st.arg0CounterEnabled ? st.arg0CounterValue : FlapsCodec.arg0iFrom01(st.arg0)
                        ));

                event.getPacket().getLongs().write(0, timeOfDay);
                event.getPacket().getLongs().write(1, p.getWorld().getTime());
                event.getPacket().getBooleans().write(0, false);
            }
        });
    }

    /** Stop ticking. Call in onDisable(). */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        states.clear();
    }

    /**
     * Get a player manager to control effects for a specific player.
     * @param player The player to control.
     * @return A {@link FlapsPlayerManager} for the specified player.
     */
    @Contract("_ -> new")
    @CheckReturnValue
    public @NotNull FlapsPlayerManager player(@NotNull Player player) {
        // ensure state exists
        states.computeIfAbsent(player.getUniqueId(), FlapsPlayerState::new);
        return new FlapsPlayerManager(this, player);
    }

    /**
     * Removes all effects for the specified player and stops sending packets.
     * @param player The player to clear.
     */
    public void clear(@NotNull Player player) {
        states.remove(player.getUniqueId());
    }

    /**
     * Allows you to provide a custom GameTime value (the value that gets communicated to the shader) per player each tick. If you choose to use this, you will need to adjust the shader code to accept your custom codec and unpack the values accordingly.
     * <p>Note that this value must be less than <b>24,000</b> as that is the maximum value the shader can understand.
     * <p>More guidance can be found in the GitHub docs.
     */
    public void setCustomTimeProvider(@org.jetbrains.annotations.Nullable BiFunction<Player, FlapsPlayerState, Long> provider) {
        this.customTimeProvider = provider;
    }

    /** Internal: apply changes requested by the builder. */
    void apply(@NotNull Player player, int maskOr, int maskAnd, Float arg0, Integer transitionTicks, Boolean arg0Counter) {
        FlapsPlayerState st = states.computeIfAbsent(player.getUniqueId(), FlapsPlayerState::new);

        // mask update
        st.mask = (st.mask & maskAnd) | maskOr;

        // arg0 mode update
        if (arg0Counter != null) {
            st.arg0CounterEnabled = arg0Counter;
            if (arg0Counter) {
                st.arg0CounterValue = 0;
                st.transitioning = false;
                st.durationTicks = 0;
            }
        }

        // arg update
        if (arg0 != null) {
            st.arg0CounterEnabled = false;
            float newArg0 = FlapsCodec.clamp01(arg0);

            int ticks = transitionTicks != null ? transitionTicks : 0;
            if (ticks <= 0) {
                st.arg0 = newArg0;
                st.transitioning = false;
                st.durationTicks = 0;
            } else {
                st.startArg0 = st.arg0;
                st.targetArg0 = newArg0;
                st.startTick = tickCounter;
                st.durationTicks = ticks;
                st.transitioning = true;
            }
        }
    }

    /**
     * Advance the transition state for a player.
     * @param st The player state to advance.
     */
    private void advance(@NotNull FlapsPlayerState st) {
        if (!st.transitioning) return;

        long elapsed = tickCounter - st.startTick;
        if (elapsed <= 0) {
            st.arg0 = st.startArg0;
            return;
        }

        if (elapsed >= st.durationTicks) {
            st.arg0 = st.targetArg0;
            st.transitioning = false;
            return;
        }

        float t = (float) elapsed / (float) st.durationTicks;
        float eased = FlapsEasing.smoothstep(t);
        st.arg0 = st.startArg0 + (st.targetArg0 - st.startArg0) * eased;
    }

    /**
     * Send an UPDATE_TIME packet to a player.
     * @param p The player to send to.
     * @param timeOfDay The packed timeOfDay value.
     */
    private void sendTimePacket(Player p, long timeOfDay) {
        sendTimePacket(p, timeOfDay, p.getWorld().getTime());
    }

    /**
     * Send an UPDATE_TIME packet to a player with explicit worldAge/timeOfDay.
     */
    private void sendTimePacket(Player p, long timeOfDay, long worldAge) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.UPDATE_TIME);

            // UPDATE_TIME: [worldAge, timeOfDay]
            packet.getLongs().write(0, timeOfDay);
            // The client appears to use the world age if time of day isn't present to drive the day/night cycle. For this reason, we still need to provide a valid time of day to avoid flickering or messing up the cycle.
            packet.getLongs().write(1, worldAge);
            packet.getBooleans().write(0, false);

            protocolManager.sendServerPacket(p, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
