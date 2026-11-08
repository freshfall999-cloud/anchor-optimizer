# Anchor Optimizer

A small **client-side** Fabric mod for **Minecraft 1.21.11** that makes respawn anchor PvP feel instant.

When you detonate a charged respawn anchor, vanilla keeps showing the anchor until the server's
block update reaches you, which takes one full ping. Until then you can't place the next anchor in that
spot because your client still thinks a block is there. With Anchor Optimizer the anchor disappears on your
screen **the moment you click**, so you can place, charge and blow up the next one right away. The higher your
ping, the bigger the difference.

It's the anchor version of what "crystal optimizer" mods do for end crystals.

## What it does and doesn't do

It does:
- remove the anchor you detonate from **your own** world view immediately, using Minecraft's built-in
  block-prediction system (the same one vanilla uses when you place or break blocks).

It doesn't:
- send any packets of its own or change the packets vanilla sends (your click sends the normal vanilla packet);
- click, swap items, place, charge or detonate anything for you (no macros, no auto-anchor, no auto-totem);
- change reach, rotations, cooldowns or your click speed;
- predict explosion damage, knockback or terrain damage. That's still all decided by the server.

The server stays in charge. If it doesn't actually explode the anchor (for example because a plugin cancelled the
click, or anchors set spawn in that dimension), the anchor comes back as soon as the server answers, the same way
a block you "broke" pops back when the server rejects it.

In the Nether, where anchors set your spawn instead of exploding, the mod does nothing.

## Is it allowed on PvP servers?

It's built to stay in the category of mod that PvP servers usually allow: client-side only, no automation, and
it sends exactly what vanilla would. **But every server makes its own rules, and some ban all "optimizer"
mods (including crystal optimizers), so no mod can promise it's allowed everywhere.** Check a server's rules
or ask staff before you use it there. If you're unsure, you can turn it off in-game (see below).

Anticheats differ too. Your next anchor reaches the server before your client has seen the server confirm the
explosion, and an anticheat that models your view very strictly could treat that differently than a vanilla
client. If you get setbacks or flags on a server, turn the mod off there.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.17.3 or newer for Minecraft 1.21.11.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 in your `mods` folder.
3. Put `anchor-optimizer-<version>.jar` in your `mods` folder.

You only need it on the client. Servers don't need it.

## Settings

- **Toggle key:** *Options → Controls → Key Binds → Anchor Optimizer → Toggle Anchor Optimizer*. It's unbound
  by default. Pressing it shows `Anchor Optimizer: ON/OFF` above your hotbar.
- **Config file:** `config/anchoroptimizer.properties` (`enabled=true` or `enabled=false`). The toggle key saves
  to this file.

## Building

You need JDK 21.

```bash
./gradlew build
```

The jar ends up in `build/libs/anchor-optimizer-<version>.jar`. Every push also builds it on GitHub Actions; you
can download it from the run's **anchor-optimizer** artifact.

### In-game tests

`src/gametest` contains a Fabric client game test. It starts a real client and a singleplayer server and checks
that:

- a detonated anchor disappears on the client right away, and the server confirms it;
- a new anchor can be placed in the same spot before the server has answered, and the server accepts it;
- a wrong prediction is rolled back to what the server says;
- with the mod turned off, behaviour is exactly vanilla;
- nothing is predicted in the Nether, where anchors set spawn.

```bash
./gradlew runClientGameTest
```

You need a display for this. On a headless Linux machine, run it under `xvfb-run -a`.

## How it works

Since 1.19, every block interaction on the client runs inside a numbered *prediction*. Any block the client
changes during that interaction is recorded together with the last state the server confirmed. When the server
acknowledges that interaction number, it has already sent the real result (which then wins), or the client
is reset to the confirmed state.

`RespawnAnchorBlock#useWithoutItem` runs on both sides, but in 1.21.11 only the server calls `explode`, and
its first step is `removeBlock(pos)`. The mod injects at the start of that method on the client and makes the same
`removeBlock` change locally, inside vanilla's prediction. It skips that step when:

- the anchor has no charge (vanilla does nothing then);
- the call isn't part of a vanilla-predicted interaction;
- the dimension type is `minecraft:the_nether`. In 1.21.11 whether anchors work is the
  `minecraft:gameplay/respawn_anchor_works` environment attribute, and the server doesn't send it to clients. Vanilla
  only enables it for the Nether dimension type, so that's what the client checks.

Because the server handles packets in the order it receives them, it has already exploded the old anchor when
your next placement packet arrives, so that placement is valid on the server too.

## License

MIT
