# Epic Fight Villager King

Minecraft 1.20.1 Forge add-on for [Epic Fight](https://modrinth.com/mod/epic-fight). It adds a boss named Villager King wearing [this NameMC skin](https://namemc.com/skin/e480685ee529111a).

## Requirements

- Minecraft 1.20.1
- Forge 47.4.4 or newer in the 1.20.1 line
- Epic Fight 20.14.17 (Forge 1.20.1)
- Java 17 for building

## Summoning

Each player's direct hits on any vanilla villager are counted across the world. On the twentieth successful hit, the Villager King appears near that player. A player cannot summon another one while their previous king lives. After it dies, that player's count starts again at zero. The counts and active boss links are saved with the world. For testing, `/summon villagerking:villager_king` also works.

## Combat

The king has 500 health, 40 armor, a base attack damage of 10 plus the equipped weapon, and continuous Regeneration III. It changes weapon every 30 seconds of active combat. Its ten weapons are uchigatana, netherite greatsword, netherite spear, netherite tachi, netherite longsword, netherite dagger, paired gloves, netherite sword, netherite axe, and trident. Swords and daggers have a 50% chance of dual wielding whenever selected.

For each weapon, the king uses the same Epic Fight player attack animations: three complete normal combos, then six weapon skill attacks, repeating until the next weapon change. Trident uses Wrathful Lighting. When a target is at least 12 blocks away, the king leaps toward it and uses Meteor Slam with any weapon. Meteor Slam has no time cooldown, though one jump must land before another can begin. The 30-second weapon switch interrupts any active attack.

## Build

Run `./gradlew build` on Linux/macOS or `gradlew.bat build` on Windows. The Forge mod JAR is written to `build/libs/`. Install it alongside the requirements above; the skin is bundled in the JAR. NeoForge and other Minecraft versions will require separate ports.

## Asset credit

The included skin is the exact PNG linked from the [NameMC skin page](https://namemc.com/skin/e480685ee529111a). Epic Fight animations are referenced from the installed Epic Fight mod; this project does not redistribute its animation assets.
