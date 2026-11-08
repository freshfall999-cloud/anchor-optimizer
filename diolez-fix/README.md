# DiolezzAnchorOptimizer, fixed for Minecraft 1.21.11

A fix of **DiolezzAnchorOptimizer 1.0.0** by Diolezz ([Modrinth](https://modrinth.com/project/pY85yYq7)). The jar's
`fabric.mod.json` says MIT, and the license file bundled in the jar is CC0; it's included here as `LICENSE`.

## The crash

The 1.0.0 jar was built for Minecraft 1.21.1 but declares `"minecraft": "~1.21"`, so it also loads on 1.21.11. Both
of its hooks call `Entity#getWorld`, intermediary `method_37908`. In 1.21.11 that method no longer exists (it's
`method_73183`, Mojang name `level()`), so the game crashed:

```
java.lang.NoSuchMethodError: 'net.minecraft.class_1937 net.minecraft.class_746.method_37908()'
	at net.minecraft.class_310.handler$zzf000$diolezanchoroptimizer$onTick(class_310.java:3659)
```

This happened as soon as the crosshair was on a block (the tick hook), and on every block right-click (the block
interaction hook).

## The fix

The same code, rebuilt against 1.21.11 with Mojang names, calling `player.level()` instead. Nothing else changed:
same mod id, same behaviour. `"minecraft"` is now `~1.21.11`, since this build only targets 1.21.11.

## What the mod actually does

- **Tick hook:** while your crosshair is on a respawn anchor, it sets the right-click delay (`rightClickDelay`)
  to 0. A *held* right-click then repeats every tick instead of every 4 ticks. Separate clicks were never limited
  by that delay, so they're unchanged.
- **Block interaction hook:** when you right-click an anchor, it resets `destroyDelay`. That's the block-*breaking*
  cooldown, so it has no effect on anchors.

## Build and test

```bash
./gradlew build                         # build/libs/diolezanchoroptimizer-1.0.1+1.21.11.jar
xvfb-run -a ./gradlew runClientGameTest # needs a display; xvfb-run on headless Linux
WITH_ANCHOR_OPTIMIZER=../build/libs/anchor-optimizer-1.1.0.jar xvfb-run -a ./gradlew runClientGameTest
```

The game test checks, in a real client:
- looking at blocks and placing blocks doesn't crash;
- holding right-click with glowstone on an anchor charges it every tick, and a single click charges once;
- with Anchor Optimizer installed too, its instant anchor removal still works.
