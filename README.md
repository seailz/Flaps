# Flaps
Flaps is a library that allows you to communicate with Minecraft resource pack shaders (different to shader packs) from a PaperMC server, allowing you to create dynamic visual effects on players screens without mods, just by providing the resource pack for players.

<p>Here's a few examples of the effects that can be achieved, but the possibilities are essentially endless:

https://github.com/user-attachments/assets/0d0d9f63-1c1d-4267-a8c0-44cce7879b1e


https://github.com/user-attachments/assets/a44e6dd3-57b6-4b8c-827c-51507e512070




https://github.com/user-attachments/assets/e8788e23-1c4f-442f-8dcf-b84a13499390

https://github.com/user-attachments/assets/5a02b76d-5fa1-49e9-8a17-aa0664a038f1



Potential use cases include:
- Dynamic visual weather effects (e.g., heat distortion during a heatwave)
- Visual effects tied to in-game events (e.g., screen distortion during earthquakes or explosions)
- Camera shake during horror scenes, boss fights, etc
- Dynamic day/night cycle effects (e.g., color grading during sunrise/sunset)
- Special effects for abilities or power-ups (e.g., motion blur when sprinting or speed boosts)

>As this project relies on modifying [core shaders](https://minecraft.wiki/w/Shader#Core-shaders), it is highly experimental and not officially supported by Mojang

## Installation and Usage
### For server owners
If you have a plugin that relies on Flaps, simply install the Flaps jar from the [latest release](https://github.com/seailz/Flaps/releases) into your `plugins` folder. You'll also need to set the relavant resource pack for your server's version (see below).
### For developers
To see detailed information on how to use Flaps in your plugin, see [the docs](https://github.com/seailz/Flaps/wiki).

## Version support
Flaps only supports versions `1.21.6` and above. Use the following resource packs for the corresponding versions:
- `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10` - use `resourcepack/1.21.8`
- `1.21.11`, `26.1` - use `resourcepack/1.21.11`

## Known issues
The complexity of modifying core shaders and the fact that it's unsupported means you **will** encounter visual bugs. Some known issues include:
- A few modded clients don't seem to support custom core shaders properly, especially when using Sodium. Installing [this](https://modrinth.com/mod/sodium-core-shader-support) mod should fix it.
- Celestial bodies are not controlled by the shader
- Riding a boat can look weird
- Holding an item in F5 view can look weird
- Shaders cannot change the lower half of the sky. There is no solution to this. (notice how the snow particles are not visible within the blue area.)
  - <img width="3440" height="1417" alt="2026-01-13_14 19 47" src="https://github.com/user-attachments/assets/8c4b8b8c-f032-4b21-b097-aea95460d423" />

- The player's hand is considered an entity and will have effects applied to it
  - The only way of detecting the hand is to check the proximity to the camera, which can lead to false positives with real mobs/entities and cause strange effects
  - For most servers, particularly for temporary effects, it's recommended to allow the hand to be affected by the shader to prevent issues with entities
  - If you want to change this behavior, uncomment the if statement in the `entity.vsh` file in the resource pack
  - There is no solution to allow the hand to be unaffected while also preventing false positives with entities

## Advanced
The plugin works by sending packets to clients to modify the `GameTime` property, which is then read by custom shaders in a resource pack. The current iteration sets an agreed upon time value (12000 ticks), which is 
then quantized as the client keeps progressing GameTime unless reset by the server and not doing so would cause drift for a few seconds. We are then left with +/- 1200 possible states per tick (shaders are stateless). Unfortunately this is the current limitation and no more data can be embedded. It *may* be possible to extend this by removing quantization and instead sending packets every tick, at an increased bandwidth and risk of losing sync with the server. 

### Resource Pack
The resource pack overrides the core shaders (located in `assets/minecraft/shaders/core/`) to apply effects from a shared file (`assets/elytraglide/shaders/include/eg_effects_vertex.glsl`) based on the modified `GameTime` value from the server.

If your server supports multiple versions, you should dynamically set the resource pack based on the player's version. Due to the significant changes in `1.21.5`, earlier versions are not supported, though the goal is to eventually introduce support for `1.20.x` and later.

### Particle effects
Particle effects can be used to communicate with post-process shaders. It is technically possible to get post-process shaders to run all the time in a way that does not impact gameplay or is limited to certain clients by using the `entity_outline` shader (the one that manages the glow effect), but despite being statefull, they run too late in the pipeline which restricts many effects (such as the roll effect). To achieve greater flexibility, I've opted to go with core shaders here as they can recreate effects much more accurately, but they're stateless and can only be communicated with every tick using `GameTIme`, causing jitter in complex effects and eliminating the possibility of client-side interpolation. If interpolation, smooth effects, and a larger amount of data communication is more important to you than the capability of effects, I'd recommend using [this great project](https://github.com/HalbFettKaese/ShaderSelectorV3) instead.
