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
- **Keep the chat clean for pictures.** Commands answer in the chat, which ends up in screenshots and videos.
  `rcon.py "gamerule sendCommandFeedback false" "gamerule logAdminCommands false"` turns that off.
- **A stage for the evaluation.** `/system evaluate [rank] [class]` plays the whole show on the nearest crystal
  within 8 blocks, or two blocks in front of the player if there is none. To put a crystal in front of Dev:

  ```
  tools/uitest/rcon.py "execute at Dev rotated ~ 0 run setblock ^ ^ ^4 beyondsolo:evaluation_crystal"
  ```
