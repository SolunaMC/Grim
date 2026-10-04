# Client and mod detection

Grim reads two things every client sends on its own:

- the **brand** (`minecraft:brand`, `MC|Brand` before 1.13), for example `vanilla`, `fabric`, `forge`,
  `lunarclient:...`;
- the **plugin channels** it registers (`minecraft:register`, `REGISTER` before 1.13), in the play and the
  configuration phase. Mods and mod loaders announce the custom payload channels they can receive this way.

From the channels Grim derives a list of detected mods and clients. Only `minecraft:register`/`REGISTER`
payloads are parsed; every other plugin message is ignored. Unregistrations are ignored, so the list
contains everything the client announced during the session. At most 256 channels are stored per player.

Everything in this document is a no-op on existing servers until configured, except the informational
additions (detected mods in `/grim profile` and the brand notification, Bedrock device info,
`/grim brands stats`).

Settings live in the `client-brand:` section of `config.yml`, texts in the `client-brand:` section of
`messages.yml`. Both are in the bundled files. Configs created before these keys existed fall back to
the defaults shown here, add the keys you want to change yourself.

## Limits of channel based detection

- A client only announces channels for mods that use custom networking. Purely client side mods (Freecam,
  minimaps without server integration, Sodium, Iris, most cheats) announce nothing and can't be detected
  this way.
- Some clients only announce a channel after the server announced the same channel. Forge (1.20.2+)
  answers the server's own registration; Lunar Client registers `lunar:apollo` for servers running
  Apollo. On a server without plugins that register channels, these may not show up.
- The channel list is client controlled. A modified client or an "anti mod detection" mod can hide or
  fake channels. Treat detections as information, not proof.
- Behind ViaVersion, legacy channel names of old clients are translated (for example `FML|HS` becomes
  `fml:hs`; names that aren't valid 1.13 identifiers, such as `WECUI`, are dropped).

## Built-in signatures

Patterns are Java regular expressions matched (`find()`) against each registered channel name.

| Name | Pattern | Evidence |
| --- | --- | --- |
| Fabric API | `^fabric(-[a-z0-9_.-]+)?:` | FabricMC/fabric-api (26.3): `ClientPlayNetworkAddon` sends `minecraft:register` with all play receivers right after the login packet, e.g. `fabric:recipe_sync`, `fabric:attachment_sync_v1`, `fabric-menu-api-v1:open_screen` (older: `fabric-screen-handler-api-v1:open_screen`) |
| NeoForge | `^neoforge:` | neoforged/NeoForge `ClientNetworkRegistry.sendInitialListeningChannels`: registers `neoforge:register`, `neoforge:network`, ... in the configuration phase when connected to a non NeoForge server |
| Forge | `^(forge\|fml):` | MinecraftForge 26.3 `ChannelListManager`/`NetworkInitialization` (`forge:login`, `forge:handshake`, ...), 1.20.1 `NetworkConstants` (`fml:handshake`, `fml:play`), ViaVersion `channelmappings-1.13.json` (`fml:hs`, `fml:mp`) |
| Forge | `^(FML\|FML\\|HS\|FML\\|MP\|FORGE)$` | MinecraftForge 1.12.x `FMLNetworkHandler`, `ForgeNetworkHandler`, `NetworkDispatcher` |
| Lunar Client | `^lunar:apollo$` | LunarClient/Apollo: the server identifies Lunar Client players by `lunar:apollo` in the client's `minecraft:register` |
| Feather Client | `^feather:client(/frag)?$` | FeatherMC/feather-server-api: replies on these channels through `Player#sendPluginMessage`, which Bukkit only delivers to channels the client registered |
| LabyMod | `^labymod:` | LabyMod/labymod4-server-api: protocol channel `labymod:neo` (addons use `labymod:<addon>`), same Bukkit reasoning as Feather |
| Simple Voice Chat | `^voicechat:` | henkelmax/simple-voice-chat: all payloads use the mod id `voicechat` |
| Plasmo Voice | `^plasmo:voice(/\|$)` | plasmoapp/plasmo-voice: `plasmo:voice/v2`, `plasmo:voice/v2/installed`, `plasmo:voice/v2/service` |
| Xaero's Minimap | `^xaerominimap:` | Release jar (Modrinth, 26.3, closed source): `xaerominimap:main`, registered through xaerolib's `ClientPacketHandlerFabric` (`ClientPlayNetworking.registerGlobalReceiver`) |
| Xaero's World Map | `^xaeroworldmap:` | Release jar (Modrinth, 26.3): `xaeroworldmap:main`, same mechanism |
| JourneyMap | `^journeymap:` | Release jar (Modrinth, 1.21.4 6.0.0-beta.47): `journeymap:version`, `journeymap:perm_req`, `journeymap:teleport_req`, ... |
| VoxelMap | `^voxelmap:` | VoxelMap-Updated release jar (Modrinth, 26.3): `voxelmap:settings` registered with `ClientPlayNetworking.registerGlobalReceiver` |
| Litematica | `^servux:litematics$` | sakura-ryoko/litematica `ServuxLitematicaHandler`, registered when joining a multiplayer world |
| MiniHUD | `^servux:(hud_metadata\|structures)$` | sakura-ryoko/minihud `ServuxHudHandler`, `ServuxStructuresHandler` |
| WorldEdit CUI | `^(worldedit:cui\|WECUI)$` | EngineHub/worldeditcui-protocol `CUIPacket`, EngineHub/WorldEditCUI `CUINetworking.CHANNEL_LEGACY` |
| Replay Mod | `^(replaymod:restrict\|Replay\\|Restrict)$` | ReplayMod/ReplayMod `Restrictions.PLUGIN_CHANNEL` |

Not included because they could not be verified, or register nothing:

- **Freecam** (MinecraftFreecam/Freecam, formerly hashalite/Freecam): no networking code, registers no
  channel. Not detectable from channels.
- **Badlion Client**: the official server API sends raw packets that bypass Bukkit's channel registration
  check, so there is no evidence that the client registers a channel.
- **LabyMod 3** (`LMC`, `labymod3:main`): not verified against a client that registers them.
- **Hacked clients**: Meteor Client and Wurst register no channel of their own (no custom payload
  receivers in MeteorDevelopment/meteor-client or Wurst-Imperium/Wurst7). Meteor's `server-spoof` module
  reports the brand `vanilla` and by default blocks `minecraft:register` altogether, which looks exactly
  like a vanilla client.
- Channels shared by several mods (`servux:entity_data`, `worldinfo:world_id`, `c:*`).

## Configuration

`config.yml`:

```yaml
client-brand:
  # Operator signatures, "regex -> name". Matched in addition to the built-in ones.
  # The last "->" separates regex and name. Invalid entries are skipped with a warning.
  mod-signatures: []
  #  - "^mymod: -> My Mod"

  # How long (ms) the staff brand notification waits after the client entered the play phase, so channels
  # registered after the brand can be listed. 0 sends it as soon as the brand arrives (mods registered
  # later are then not listed). Players who leave within this time produce no notification.
  notification-delay-ms: 1000

  # Rules, evaluated once the brand or registrations are known (in any order).
  # Each rule acts at most once per player session.
  #   regex:   Java regular expression, matched with find()
  #   type:    brand -> matched against the client brand
  #            mod   -> matched against detected mod names and the raw registered channel names
  #   action:  alert -> message to staff with alerts enabled
  #            kick  -> disconnect the player (logged to the console)
  #   message: optional, overrides rule-alert-format / rule-kick-message (messages.yml) for this rule,
  #            same placeholders
  rules: []
  #  - regex: "(?i)^wurst"
  #    type: brand
  #    action: kick
  #  - regex: "^Xaero's Minimap$"
  #    type: mod
  #    action: alert
  #    message: "%prefix% &f%player% &buses Xaero's Minimap"

  # BadPacketsT, brand spoof check. Nothing is flagged unless enabled.
  spoof-check:
    enabled: false
    # Brand exactly "vanilla" (case-insensitive) while the client registers channels only a mod loader
    # registers (Fabric API "fabric*:", Forge "forge:"/"fml:"/legacy FML, NeoForge "neoforge:").
    vanilla-with-mod-loader: true
    # A different brand later in the same session. Opt-in: the vanilla client sends its brand once per
    # connection, but proxies or mods may resend it.
    brand-change: false

  # /grim brands stats
  stats:
    # Most entries per section
    max-entries: 10
```

`messages.yml`:

```yaml
client-brand:
  # Appended to client-brand-format when mods were detected and the format has no %mods%.
  mods-suffix: " &7(mods: &f%mods%&7)"
  # Inserted before the last line of the "profile" message when mods were detected and the profile
  # has no %mods% placeholder.
  profile-mods-line: "&bMods: &f%mods%"

  # Shown by /grim profile for Bedrock (Geyser/Floodgate) players, who are exempt from Grim.
  # Placeholders: %player%, %bedrock_device%, %bedrock_input%
  bedrock-profile:
    - "&7======================"
    - "%prefix% &bProfile for &f%player%"
    - "&bBedrock player &7(exempt from checks)"
    - "&bDevice: &f%bedrock_device%"
    - "&bInput: &f%bedrock_input%"
    - "&7======================"

  # Default texts of the rules in config.yml.
  # Placeholders in rule messages: %player% and the other player placeholders, %match% (the matched
  # brand, mod or channel), %rule% (the regex)
  rule-alert-format: "%prefix% &f%player% &bmatched client rule &f%rule% &7(%match%)"
  rule-kick-message: "<red>Your client or one of your mods is not allowed on this server."

  # /grim brands stats
  stats:
    header: "%prefix% &bClients of &f%players% &bonline players &7(online players only)"
    brands: "&bBrands:"
    mods: "&bDetected mods:"
    versions: "&bClient versions:"
    entry: " &7- &f%name%&7: &f%count% &7(%percent%%)"
    more: " &7... and &f%count% &7more"
    none: " &7- none"
```

### Placeholders

`%mods%` is available wherever player placeholders work (alerts, `profile`, `client-brand-format`, ...).
It lists the detected mods separated by commas, or `none`.

## Brand spoof check (BadPacketsT)

Off unless `client-brand.spoof-check.enabled: true`. Flags at most once per condition and session:

- `brand=..., channel=...`: brand exactly `vanilla` but a mod loader channel was registered. Lunar Client,
  Feather and other clients reporting their own brand are not affected, nor are honest `fabric`/`forge`
  brands.
- `first=..., now=...`: a different brand later in the same session (needs `brand-change: true`).

Brands are compared after the existing normalisation (Velocity's ` (Velocity)` suffix and colour codes are
removed). Bedrock players (Geyser/Floodgate) are never checked.

BadPacketsT is part of the default `BadPackets` punishment group, whose alert threshold a once per session
flag won't reach. To be alerted, add a group such as:

```yaml
  BrandSpoof:
    remove-violations-after: 300
    checks:
      - "BadPacketsT"
    commands:
      - "1:1 [alert]"
      - "1:1 [log]"
```

The name BadPacketsT was used by a different check before it was renamed to InvalidInteractCursor. Old
history rows keep their own stable key (`grim.badpackets.invalid_interact_vector`); the spoof check uses
`grim.badpackets.brand_spoof`.

## /grim brands stats

Permission `grim.brand.stats` (default op). Shows the distribution of brands, detected mods and client
versions among online players tracked by Grim (Bedrock players are exempt and not counted). History is not
included: the datastore has no aggregate query over session brands.

## Bedrock devices

`/grim profile` on a Bedrock player shows the device OS and input mode from the Floodgate API
(`FloodgatePlayer#getDeviceOs`, `#getInputMode`) or, without Floodgate, the Geyser API
(`Connection#platform`, `#inputMode`). Both are optional; without them the previous "exempt or offline"
message is shown.

## Translation key detection: not implemented

Mods without channels can sometimes be detected with the "sign translation vulnerability" (MC-265322):
the server opens a sign editor whose text contains a mod's translation or keybind key and reads back what
the client resolved it to. Checked against decompiled vanilla clients 1.21.11, 26.1-snapshot-1, 26.3 and
26.4-snapshot-2: `AbstractSignEditScreen` still turns the lines into plain strings with
`Component#getString()` (resolving translation and keybind components) and sends them back in
`ServerboundSignUpdatePacket` when the screen closes; `AnvilScreen` does the same for item names. So
Mojang has not stopped this as of 26.3/26.4-snapshot-2.

It is still not implemented, because it is neither safe nor reliable for an anticheat:

- It is invasive: it needs a fake sign block next to the player, force-opens and closes a GUI (closing
  any open inventory and interrupting the player), and fakes world state that Grim's own world and
  prediction tracking would see.
- It is unreliable: many mods block it (ModDetectionPreventer, ExploitPreventer, OpSec, Wurst,
  older Meteor versions); results depend on the client language and keybinds, and missing answers can't
  be told apart from protection.
- It abuses a vulnerability that clients actively patch, which may break at any time.

There is therefore no `client-brand.translation-detection` setting.
