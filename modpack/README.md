# Pack files

Files the CurseForge playtest pack ships next to the mods. A pack build copies `overrides/` into the pack zip's
`overrides/` (beside `overrides/mods/`, which holds our jar). CurseForge applies overrides on install and on every
pack update.

- `overrides/config/xaerohud.txt`: Xaero's minimap in the top-right corner. Xaero's default is the top left, where
  the System's status capsule and quest cards are. The System's HUD makes room for the minimap in either top corner
  (`client.hud.HudSpace`).
