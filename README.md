# Anchor Optimizer

A small **client-side** Fabric mod for **Minecraft 1.21.11** that makes respawn anchor PvP feel instant.

When you detonate a charged respawn anchor, vanilla keeps showing the anchor until the server's block update
reaches you, which takes one full ping. During that time your client is showing you an anchor that's already
gone, so your next clicks go to the wrong place: glowstone ends up on the floor, or the next anchor lands next to
the spot instead of on it. With Anchor Optimizer the anchor disappears on your screen **the moment you click**, so
your next place, charge and detonate clicks hit what they should. The higher your ping, the bigger the difference.

It's the anchor version of what "crystal optimizer" mods do for end crystals.

## Tested on a real server

The client was driven with real key presses and right clicks at 20 ticks per second. It connected through a proxy
that adds 100 ms of ping (the tab list showed 102 ms) to a **Paper 1.21.11** server (build 132). "Fast loop" means
place → charge once → detonate, one anchor every 300 ms with the crosshair kept on the spot, 20 anchors in a row.

**Paper, no anticheat**

|                                      | Mod on       | Mod off   |
|--------------------------------------|--------------|-----------|
| Detonated anchor disappears after    | ~49 ms       | ~139 ms   |
| Fast loop: anchors detonated         | **20 / 20**  | 12 / 20   |
| Fast loop: clicks on the wrong thing | 0 / 60       | 52 / 60   |

**Paper + GrimAC 2.3.75**

|                                      | Mod on (default: paused automatically) | Prediction forced on | Mod off   |
|--------------------------------------|----------------------------------------|----------------------|-----------|
| Detonated anchor disappears after    | ~145 ms                                | ~44 ms               | ~145 ms   |
| Fast loop: anchors detonated         | 13 / 20                                | 11 / 20              | 12 / 20   |
| Grim flags during the fast loop      | **0**                                  | 9 (`AirLiquidPlace`) | 2 (`AirLiquidPlace`) |

Grim checks every action against what your client had actually received at that moment. Placing the next anchor
before the server's "anchor is gone" update has reached you looks impossible to it, so it flags that action and
cancels it. No client-side trick legitimately avoids this. That's why the mod **detects these anticheats and pauses
itself** (see below), so you play exactly like vanilla there. Grim also flagged plain vanilla play twice in this
very fast loop. In an earlier run it flagged vanilla play 0 times.

Caveat: the test client handles incoming packets once per tick (50 ms) instead of every frame, so the "mod off"
times are up to one tick later than a real high-FPS client would see. "Mod on" times are one frame of a
software-rendered client. See [`servertest/README.md`](servertest/README.md) to rerun it against your own server.

## What it does and doesn't do

It does:
- remove the anchor you detonate from **your own** world view immediately, using Minecraft's built-in
  block-prediction system (the same one vanilla uses when you place or break blocks);
- pause itself on servers whose anticheat checks what your client has already received (GrimAC and similar),
  and tell you in chat when it does.

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

**No mod can promise that. Every server makes its own rules, and some ban all "optimizer" mods (including crystal
optimizers).** Check a server's rules or ask staff before you use it there.

What the mod does to stay safe:
- It's client-side only, has no automation, and sends exactly what vanilla would.
- On servers running an anticheat that checks what your client has received (GrimAC and similar), acting on a
  prediction gets flagged. The mod notices these servers because they keep sending "transaction" ping packets,
  which a vanilla server never does during play. It then turns the prediction off for that server, and you see
  *"[Anchor Optimizer] This server's anticheat checks what your client has already received, so anchor prediction
  is paused here"* in chat. In the Grim test above that meant 0 flags from the mod.
- Only GrimAC was tested. Anticheats that don't send transaction pings aren't detected. If you get setbacks or
  flags anywhere, turn the mod off there.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.17.3 or newer for Minecraft 1.21.11.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 in your `mods` folder.
3. Put `anchor-optimizer-<version>.jar` in your `mods` folder.

You only need it on the client. Servers don't need it.

## Settings

- **Toggle key:** *Options → Controls → Key Binds → Anchor Optimizer → Toggle Anchor Optimizer*. It's unbound
  by default. Pressing it shows `Anchor Optimizer: ON/OFF` above your hotbar.
- **Config file:** `config/anchoroptimizer.properties`
  - `enabled` (default `true`): turns the prediction on or off. The toggle key saves to this.
  - `pauseOnAnticheatServers` (default `true`): pauses the prediction on servers detected as above. Setting it
    to `false` keeps predicting there, which in the test above got flagged on about every other anchor and made
    you slower.

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
- nothing is predicted in the Nether, where anchors set spawn;
- once the server sends a transaction ping, prediction pauses and behaviour is exactly vanilla.

```bash
./gradlew runClientGameTest
```

You need a display for this. On a headless Linux machine, run it under `xvfb-run -a`. To test against a real
server instead, see [`servertest/README.md`](servertest/README.md).

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
  only enables it for the Nether dimension type, so that's what the client checks;
- the server has sent a ping packet during play (a transaction-based anticheat), unless `pauseOnAnticheatServers`
  is `false`.

Because the server handles packets in the order it receives them, it has already exploded the old anchor when
your next placement packet arrives, so that placement is valid on a vanilla or Paper server.

## License

MIT
