# JetBrains Lazygit

![Build](https://github.com/mym0404/jetbrains-lazygit-runner/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28199.svg)](https://plugins.jetbrains.com/plugin/28199)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28199.svg)](https://plugins.jetbrains.com/plugin/28199)

A JetBrains IDE plugin that integrates [lazygit](https://github.com/jesseduffield/lazygit) - a simple terminal UI for git commands - directly into your development workflow.

## Features

- **Quick Action**: Access lazygit instantly with Jetbrains Action
- **Smart Terminal Integration**: Fast shell terminal runner optimized for lazygit (no profile)
- **Cross-Platform Support**: Works on Windows, macOS, and Linux
- **Environment Configuration**: Automatically sets up proper config directories (XDG on Unix, AppData on Windows)

## Requirements

- JetBrains IDE (IntelliJ IDEA, WebStorm, PyCharm, etc.) version 2024.3 or later
- [lazygit](https://github.com/jesseduffield/lazygit) installed on your system

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "JetBrains Lazygit"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28199) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/28199/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/mym0404/jetbrains-lazygit-runner/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
