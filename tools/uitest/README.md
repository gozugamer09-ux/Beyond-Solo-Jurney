# Looking at the game without a screen

These scripts run a dev server and a dev client on a virtual screen, so the System UI and the animations can be
looked at, screenshotted and recorded on a machine that has no monitor (a build box, a cloud container). They drive
the small remote control in `client/UiTestDriver`.

They need Linux with `Xvfb`, a software OpenGL driver (Mesa llvmpipe), `python3` and, for videos, `ffmpeg`.

```
tools/uitest/setup.sh     # once per checkout: accepts the Minecraft EULA, writes the server and client settings
tools/uitest/start.sh     # virtual screen + server + client; returns when the client has joined

tools/uitest/ui.sh "screen status" "wait 20" "shot status" "close"         # -> runs/client/screenshots/status.png
tools/uitest/rcon.py "time set 13000" "weather clear"                      # server commands
tools/uitest/record.sh reveal 5 "cmd system evaluate s mage" "wait 400"    # -> build/uitest/rec-reveal.mp4

./gradlew processResources && tools/uitest/ui.sh "reload"                  # after a change to models, animations, textures
tools/uitest/client.sh    # restart only the client, after a change to client code
tools/uitest/stop.sh      # stop everything; after a change to server code, stop and start again
```

`ui.sh` takes the commands listed in the class comment of `client/UiTestDriver` and returns when they have all run,
a final `wait` included. Waits are in game ticks, 20 to a second. The logs are `build/uitest/server.log` and
`build/uitest/client.log`. Starting takes a minute or two, and much longer the first time in a new checkout, while
Gradle sets the game up.

## Things to know

- **It is slow.** Software rendering manages about 17 frames a second at 1280x720. Screenshots do not care. For
  video, `record.sh` runs the game several times slower while it records and speeds the result back up, which gives
  a smooth video at normal speed.
- **There is no sound.** Check sounds on a real machine.
- **The client sometimes fails to start.** A library mod (Prickle) can lose a start-up race when the game runs from
  Gradle. The log then says "broken mod state". `client.sh` notices and starts the client again, up to four times.
- **GUI scale.** The window is 1280x720 and the scripts set GUI scale 2. `ui.sh "guiscale 3"` is the largest that
  fits in that window.
- **The test player is an operator called Dev**, so `ui.sh "cmd ..."` can run any command as the player.
  `look.py` prints the `tp` command that stands Dev at one point looking at another.
- **Filming the player from outside.** `ui.sh "camera <x> <y> <z> <yaw> <pitch>"` films from a fixed point, like a
  camera on a tripod, so what the player casts is seen from the side instead of hiding behind their back. The
  numbers are the ones `look.py` prints. `camera off` goes back to the player's own eyes.
- **Pressing buttons.** `ui.sh "click <x> <y>"` moves the mouse to a point of the open screen and left-clicks it, in
  GUI coordinates (at GUI scale 2 the window is 640x360). That is how a tab, a row or a button of any screen gets
  pressed. Find the point on a screenshot: halve its pixel position at GUI scale 2.
- **Another language.** `ui.sh "lang es_es"` switches the game to Spanish (and `lang en_us` back) without a
  restart, as the options screen does.
- **Keep the chat clean for pictures.** Commands answer in the chat, which ends up in screenshots and videos.
  `rcon.py "gamerule sendCommandFeedback false" "gamerule logAdminCommands false"` turns that off.
- **A stage for the evaluation.** `/system evaluate [rank] [class]` plays the whole show on the nearest crystal
  within 8 blocks, or two blocks in front of the player if there is none. To put a crystal in front of Dev:

  ```
  tools/uitest/rcon.py "execute at Dev rotated ~ 0 run setblock ^ ^ ^4 beyondsolo:evaluation_crystal"
  ```

## Measuring how much a screen costs

`ui.sh "screen storage" "wait 60" "perf storage 150"` logs, after 150 frames, the average frame time, the open
screen's share of it, and how many System shapes and shape draw calls a frame took (`client/UiPerf`, in
`runs/client/logs/latest.log`). The machine draws in software and is slow at blur and large fills, so compare these
numbers with each other, never with a real PC. `rcon.py "tick freeze"` stops mobs and the sky, so that two runs see
the same world (`tick unfreeze` after).

To count every OpenGL draw call of a frame, vanilla's and other mods' included, start the client with the small
counter in `glcount/`. It sits between the game and the OpenGL library:

```
gcc -shared -fPIC -O2 -o build/uitest/libglcount.so tools/uitest/glcount/glcount.c \
    -Wl,--no-as-needed -ldl -lpthread /usr/lib/x86_64-linux-gnu/libGL.so.1
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dorg.lwjgl.opengl.libname=$PWD/build/uitest/libglcount.so" \
    GLCOUNT_FILE=$PWD/build/uitest/glcount.txt tools/uitest/client.sh
tools/uitest/ui.sh "close" "wait 40" "perf world 150" "screen storage" "wait 60" "perf storage 150" "close"
python3 tools/uitest/glcount/drawcalls.py build/uitest/glcount.txt runs/client/logs/latest.log
```

The last line prints the draw calls a frame of every `perf` run in the log; a screen's own share is its number minus
the `world` one. Start the client again without the counter afterwards (`tools/uitest/client.sh`).
