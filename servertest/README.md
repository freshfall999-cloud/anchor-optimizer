# Testing against a real server

`ServerAnchorTest` (in `src/gametest`) connects a real client with the mod to a server you run, plays a fast
anchor loop with real key presses and clicks, and reports how fast detonated anchors disappear and how many
anchors actually land. It does this three times: with the mod **ON** (default settings), **FORCED** (prediction even
where an anticheat was detected) and **OFF** (vanilla).

**Use a throwaway test server.** The test rebuilds an 11×11 arena around x=4, z=4 at y=-61 to -56, and changes the
test player's game mode, effects, attributes and inventory.

## 1. Server

Any 1.21.11 server with RCON, for example Paper, with or without an anticheat plugin. In `server.properties`:

```properties
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
enable-rcon=true
rcon.port=25575
rcon.password=anchortest
server-port=25566
```

To see every anticheat flag in the console with GrimAC, set each `[alert]` threshold in
`plugins/GrimAC/punishments.yml` to `"1:1 [alert]"` **before** starting the server. Otherwise Grim alerts only
after 5–20 violations. Grim also records every violation in `plugins/GrimAC/data/history.v1.db`.

## 2. Ping (optional)

```bash
python3 servertest/latency_proxy.py 25570 127.0.0.1:25566 50
```

This adds 50 ms each way (100 ms round trip). Connect the client to port 25570 instead of 25566.

## 3. Run

The test framework normally syncs packets with a server running in the same process. For an external server,
that sync has to be turned off with `-Dfabric.client.gametest.disableNetworkSynchronizer=true`.

```bash
ANCHOR_TEST_SERVER=127.0.0.1:25570 \
ANCHOR_TEST_RCON=127.0.0.1:25575 \
ANCHOR_TEST_RCON_PASSWORD=anchortest \
ANCHOR_TEST_REPORT=$PWD/server-report.txt \
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric.client.gametest.disableNetworkSynchronizer=true" \
xvfb-run -a ./gradlew runClientGameTest
```

Optional variables:
- `ANCHOR_TEST_CYCLES`: anchors per fast loop (default 20).
- `ANCHOR_TEST_LABEL`: title line for the report.
- `ANCHOR_TEST_VERBOSE=1`: log every click with what the crosshair hit and what the server had.

The test writes `[anchortest] <phase> begin/end` markers to the server console, so you can match anticheat alerts
to the mode that caused them.
