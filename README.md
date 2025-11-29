Setup/Usage
**1. Installation**
   1A. Download Feather Server API and place it in your plugins folder.
   1B. Download and purchase FeatherUtils and place it in your plugins folder.

**2. Banner**
   2A. Create a folder ``backgrounds`` in ``FeatherClientUtility-Paper`` of your ``plugins`` folder.
   2B. Upload a banner (Must be a PNG) with a max size of 1009x202 (Recommended 909x102).

**3. Global Mods**
   [Easy] In-game use ``/featherutils mod disable <mod>`` to disable mods, this can be undone by using ``/featherutils mod enable <mod>``.
   [Advanced] In ``config.yml`` under banned-mods, list all mods you want to ban using valid JSON formating.

**4. Waypoints ( <required> [optional] )**
   4A. Waypoints can be set using ``/featherutils waypoints create <name> <x> <y> <z> [color] [time]``
   4B. Waypoints can be modified using ``/featherutils waypoints modify <name> <x> <y> <z> [color]``
   4C. Waypoints can be deleted using ``/featherutils waypoints delete <name>``

**5. Individual Player Mods**
   5A. You can disable individual player mods for a session using ``/featherutils playermod <player> <mod> disable``
   5A. You can enable individual player mods for a session using ``/featherutils playermod <player> <mod> enable`` (Note that you can only enable mods that the player was using before it was disabled)

**6. Misspenalty**
   i. Feather Client removes the misspenalty on servers to allow users to hit, bypassing the penalty.
   6A. This can be disabled using ``/featherutils misspenalty ban``
   6B. You can allow it using ``/featherutils misspenalty allow``

## NOTICE
This plugin is Open Source for submitting issues, creating pull requests, or compiling for your own uses. However, [purchasing a license](https://builtbybit.com/resources/featherutils.84554/) is required for servers with more than 75 concurrent online players!

Refunds are not possible if you do not come to us for support on our Discord and/or bought the plugin for a version not officially supported (Supported Versions are listed).

FeatherMC is a product of Digital Ingot ©2025; Disc Realms Studios ©2025 does not have any official relation with FeatherMC or Digital Ingot.