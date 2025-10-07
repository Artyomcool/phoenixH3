# Project: Phoenix  
**Project: Phoenix** is a JVM-based runtime patching framework for *Heroes of Might and Magic III*.  

It provides a foundation for mods that can be **embedded directly into the map** and executed at runtime — without any external plugins or patch managers.  
The modification code is shipped together with the map and loaded dynamically, making each scenario fully self-contained.

## Demonstration

In the demo build, three **new artifacts** were added to the game:

- **Ring of Phoenix** — grants phoenixes full resurrection after battle.  
- **Token of Coward** — alters combat behavior by affecting fear and cost evaluation in battle logic.  
- **Phoenix Forge** — a configurable creature bank that replaces graphics and behavior directly from map events.  

These examples demonstrate how the engine can be patched and extended **from within a map script**, without modifying the game binaries.
![image](https://github.com/user-attachments/assets/dd1435da-5467-4144-867a-75e380d01f82)
![image](https://github.com/user-attachments/assets/05fc5a10-4ce3-406d-88b5-881c6b6c4a9a)
![image](https://github.com/user-attachments/assets/d8d44b54-c3bb-45f3-96a7-40dee841409f)
![image](https://github.com/user-attachments/assets/04dee23b-4fbf-408b-a596-938500197783)
![image](https://github.com/user-attachments/assets/fa12ff86-9642-4549-9add-e907e2d10440)


> Each map can define it's own new artifacts, creature banks, etc entirely through embedded Java modules.

## Features
- Embed mods directly into `.h3m` maps.  
- Fully open-source — no closed libraries or proprietary dependencies.  
- All mods and projects built on Phoenix must remain open-source and use a compatible license.  
- Supports runtime engine patching and extensions without modifying the original binaries.

## Purpose
Phoenix enables map creators to define their own mechanics, rules, and logic — all within a single file, fully under the author’s control.  
Each map becomes not just a scenario, but a complete standalone modification.

## Philosophy
Phoenix was built for fun — a technical experiment to see how far *Heroes III* can be pushed.  
Its main goal is already achieved: proving that the game can be modified directly from the JVM.  
If the project gains interest from the community, future development will focus on technology — improving tooling, simplifying patch creation, and expanding framework capabilities.

## License
- **Core (`src/`)** — GPL-2.0  
- **Build tools (`buildSrc/`)** — Apache-2.0  
- All derivative projects must remain open-source under a compatible license.

## Running
To start Phoenix, make sure you have JDK 17 installed, and then:  
```bash
./gradlew phoenixRun
```
And follow the instructions.
