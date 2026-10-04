# Experimental Bedrock support

> [!WARNING]
> This lives on the `experimental/bedrock` branch and is **off by default**. Test it on a
> staging server before using it anywhere else.

Out of the box, Grim exempts Bedrock players (Geyser/Floodgate) completely. Bedrock
clients don't run Java movement physics, so Grim's movement prediction can't be applied
to them.

With the experimental support turned on, Bedrock players are tracked instead of exempted:

- They run in the same mode as players with `grim.disabled`: no movement simulation,
  no setbacks, no reach check, and no packet modification by disabled checks.
- Only the checks listed in `bedrock.checks` may flag them, and only those can cancel or
  change their packets. The default list contains the packet sanity checks (`Crash*`,
  `Exploit*`). They only validate protocol values such as world bounds, slot ids, NaN
  rotations, book and anvil contents, and don't depend on movement physics.
- `grim.disabled` and `grim.exempt` keep working for Bedrock players as usual.

## Enabling it

Add this to `plugins/GrimAC/config.yml` (or the Fabric config folder) and run `/grim reload`:

```yaml
bedrock:
  # Track Bedrock players instead of exempting them
  enabled: true
  # Checks that may flag Bedrock players. Matching works like punishments.yml:
  # "Exploit" matches ExploitA and ExploitB, "CrashA" only CrashA.
  checks:
    - Crash
    - Exploit
```

Bedrock players who are online during the reload are picked up the next time they join.

## Known limits

- Geyser translates Bedrock packets into Java packets. A translation quirk can look like
  a protocol violation, so watch the alerts before adding punishments for Bedrock players.
- Movement, combat (reach, aim) and timer checks stay off for Bedrock players. Supporting
  them would need a Bedrock physics model, which Grim doesn't have.
- The options are read with defaults and aren't in the bundled `config.yml`, so this
  branch doesn't bump the config version away from upstream.
