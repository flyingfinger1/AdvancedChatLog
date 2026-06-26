# AdvancedChatLog

AdvancedChatLog lets you view and search through the chat history, beyond what Minecraft usually lets you view.

> **Refurbished fork.** DarkKronicle archived the original project. This fork brings AdvancedChatLog
> up to **Minecraft 26.2** and modernises the codebase: ported from Yarn to the new Mojang names and
> the 26.x APIs (incl. Component↔JSON via `ComponentSerialization.CODEC`). It is a module of the
> refurbished [AdvancedChatCore](https://github.com/flyingfinger1/AdvancedChatCore).

## Requirements

| | Version |
| --- | --- |
| Minecraft | **26.2** |
| Java | **25** (required by Minecraft 26.x) |
| Fabric Loader | 0.19.0+ |

## Dependencies

The following are **required** for this mod to run:

- [AdvancedChatCore](https://github.com/flyingfinger1/AdvancedChatCore) **1.6.2+** (this fork's build)
- [MaLiLib](https://modrinth.com/mod/malilib) — for 26.x use the sakura-ryoko builds
- [Fabric API](https://modrinth.com/mod/fabric-api)

[Mod Menu](https://modrinth.com/mod/modmenu) is recommended to open the configuration screen.

## Features

- View chat history beyond the vanilla Minecraft limit of 100 lines
- Search through chat history using RegEx, literal, and case-insensitive (UpperLower)
- Retain chat history across relaunching
- Smooth Scrolling

## Building

The build needs a **JDK 25** toolchain (Minecraft 26.x). AdvancedChatLog depends on the refurbished
AdvancedChatCore, which it resolves from your local Maven repository. Publish Core locally first:

```
# in the AdvancedChatCore clone
./gradlew publishToMavenLocal      # publishes io.github.darkkronicle:AdvancedChatCore:1.6.2

# then in AdvancedChatLog
./gradlew build
```

To run the mod, install it together with AdvancedChatCore, MaLiLib and Fabric API.

## Development

To ensure code consistency the hook `pre-commit.sh` can be used. To install it run:

`ln -s ../../pre-commit.sh .git/hooks/pre-commit`

## Credits n' more

- Code & Mastermind: DarkKronicle
- Language & Proofreading: Chronos22
- 26.2 port & modernisation: community fork
