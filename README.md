# WMInventoryControl

WMInventoryControl is a minecraft plugin addon for WeaponMechanics where you can limit the amount of identical weapons a player can use per inventory.

---

## Requirements

- Java 21
- This plugin needs `WeaponMechanics`and `NBTAPI` to run.

---

## Features

- Restricts players from using multiple instances of the same weapon
- Restricts the usage of 2 or more weapons if they are in the same group

---

## Configuration

The plugin uses a configuration file to set the maximum allowed number of identical weapons per player. After running the plugin for the first time, a default configuration file, like the one showed bellow will be generated in the `plugins/WMInventoryControl` directory. Modify it to suit your server's needs and reload the plugin to apply changes.

---

```properties
#debug mode
debug-mode: true
#weapon limits
weapon-limits:
AUG: 2
AK_47: 1
M4A1: 1
AS50: 1
REVOLVER: 2
```

---

## How it works

When a player shoots a weapon from `WeaponMechanics` that is listed in the plugin's `config.yml` file, it will mark said weapon if the limit is not reached.

For example, if we list a weapon followed by a number, that number will represent the max amount of weapons a player can mark and in case the number is specified, the default value is 1.

If a player attempts to mark a weapon which limit is reached, the player won't be able to shoot it because it does not bear the marked tag.

If a player drops, removes, stores, gets eliminated or loses the weapon in any way the weapon will get `unmarked`.

Only `marked` weapons will work!

---

## Acknowledgments

- Special thanks to the creators of WeaponMechanics, `DeeCaaD` and `CJCrafter` for their API.
- Special thanks to the creator if NBTAPI, `tr7zw` and those who contributed to the plugin's development.

---

## License
**WeaponMechanics Inventory Control: A plugin designed to prevent players from spamming multiple instances of the same weapon.**  
**Copyright (C) 2024 DMariusM**

WMInventoryControl is licensed under the GNU General Public License v3.0 (GPL-3.0). You are free to modify, redistribute, and use this software under the terms of the GPLv3 license.

See the full license text in the [LICENSE](LICENSE) file.

---

### Disclaimer

This plugin is provided "as is" without any warranty. The author is not responsible for any damage caused by the use or misuse of this plugin.
It is recommended to test the