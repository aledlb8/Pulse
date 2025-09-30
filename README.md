<p align="center">
  <img src="https://github.com/user-attachments/assets/03b5894d-6281-4459-82d3-0b8b1f025d25" alt="pulse" width="200">
</p>
﻿<h1 align="center">Pulse</h1>
<p align="center">Modular Paper & Folia server core for Minecraft 1.21+, powered by Kotlin.</p>
<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Build" src="https://img.shields.io/badge/Gradle-Shadow-green?logo=gradle&logoColor=white" />
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" />
</p>

## Overview
Pulse unifies ranks, permissions, economy, chat, shops, tags, punishments and placeholders into one lightweight core for Paper and Folia servers. Every subsystem is modular, asynchronous, and backed by Exposed + HikariCP storage.

## Key Features
- Unified manager layer for core gameplay systems
- Automatic PlaceholderAPI expansion and Vault hooks when available
- YAML-driven configuration with versioned resource processing

## Quick Start
1. Download the latest Pulse jar and drop it in `plugins/`.
2. Start your server to generate `plugins/Pulse/` defaults.
3. Tweak the YAML configs and reload with `/pulse reload`.

## Build Locally
```bash
# Windows
gradlew.bat shadowJar

# macOS / Linux
./gradlew shadowJar
```
The shaded jar is created at `build/libs/Pulse-1.0-all.jar`.

## Permissions
<details>
<summary>Click to view all permissions</summary>

### Root Permissions
- `pulse.*` - All pulse permissions
- `pulse.admin` - Administrator access to pulse
- `pulse.chat.color` - Use color codes in chat messages

### Rank Permissions
- `pulse.rank` - Access to rank commands (includes all sub-permissions)
- `pulse.rank.create` - Create new ranks
- `pulse.rank.delete` - Delete existing ranks
- `pulse.rank.set` - Set player ranks
- `pulse.rank.info` - View rank information
- `pulse.rank.list` - List all ranks
- `pulse.rank.permission` - Manage rank permissions
- `pulse.rank.reload` - Reload rank system

### Permission Management
- `pulse.permission` - Access to permission commands (includes all sub-permissions)
- `pulse.permission.add` - Add permissions to players
- `pulse.permission.remove` - Remove permissions from players
- `pulse.permission.deny` - Deny permissions for players
- `pulse.permission.check` - Check player permissions
- `pulse.permission.list` - List player permissions

### Gamemode Permissions
- `pulse.gamemode` - Change gamemode (includes all sub-permissions)
- `pulse.gamemode.creative` - Change to creative mode
- `pulse.gamemode.survival` - Change to survival mode
- `pulse.gamemode.adventure` - Change to adventure mode
- `pulse.gamemode.spectator` - Change to spectator mode
- `pulse.gamemode.others` - Change others' gamemode

### Punishment Permissions
- `pulse.punishment` - All punishment permissions (includes all sub-permissions)
- `pulse.punishment.kick` - Kick players
- `pulse.punishment.warn` - Warn players
- `pulse.punishment.warns` - View player warnings
- `pulse.punishment.unwarn` - Remove warnings
- `pulse.punishment.mute` - Mute players
- `pulse.punishment.unmute` - Unmute players
- `pulse.punishment.freeze` - Freeze/unfreeze players
- `pulse.punishment.tempban` - Temporarily ban players
- `pulse.punishment.ban` - Permanently ban players
- `pulse.punishment.unban` - Unban players
- `pulse.punishment.ipban` - IP ban players
- `pulse.punishment.tempipban` - Temporarily IP ban players

### Economy Permissions
- `pulse.coin` - Access to coin commands (includes pay permission)
- `pulse.coin.add` - Add coins to players
- `pulse.coin.remove` - Remove coins from players
- `pulse.coin.set` - Set player coin balances
- `pulse.coin.pay` - Pay other players
- `pulse.coin.top` - View balance leaderboard
- `pulse.coin.others` - Check other players' balances

### Shop Permissions
- `pulse.shop` - Access to shop (includes use permission)
- `pulse.shop.use` - Use the shop
- `pulse.shop.reload` - Reload shop configuration
- `pulse.shop.list` - List shop items

### Tag Permissions
- `pulse.tag` - Access to tag commands (includes all sub-permissions)
- `pulse.tag.others` - View other players' tags
- `pulse.tag.give` - Give tags to players
- `pulse.tag.remove` - Remove tags from players
- `pulse.tag.create` - Create new tags
- `pulse.tag.edit` - Edit existing tags
- `pulse.tag.delete` - Delete tags
- `pulse.tag.reload` - Reload tag system

</details>

## Placeholders
<details>
<summary>Click to view all placeholders</summary>

All placeholders use the format `%pulse_<placeholder>%`

### Rank Information
- `%pulse_rank%` - Player's rank ID
- `%pulse_rank_name%` - Player's rank name
- `%pulse_rank_prefix%` - Player's rank prefix
- `%pulse_rank_suffix%` - Player's rank suffix
- `%pulse_rank_weight%` - Player's rank weight
- `%pulse_rank_is_default%` - Whether player has default rank (true/false)

### Player Display
- `%pulse_player_formatted%` - Formatted player display name
- `%pulse_player_display%` - Player display name (alias)
- `%pulse_player_prefix%` - Player's prefix
- `%pulse_player_suffix%` - Player's suffix
- `%pulse_player_name_formatted%` - Player name with prefix and suffix

### Permission Information
- `%pulse_permissions_count%` - Total permissions count
- `%pulse_permissions_total%` - Total permissions count (alias)
- `%pulse_permissions_player_count%` - Player-specific permissions count
- `%pulse_permissions_rank_count%` - Rank permissions count
- `%pulse_permissions_denied_count%` - Denied permissions count

### Economy
- `%pulse_balance%` - Player's balance (formatted with currency symbol)
- `%pulse_money%` - Player's balance (alias)
- `%pulse_balance_raw%` - Player's balance (raw number)
- `%pulse_currency_name%` - Currency name (singular)
- `%pulse_currency_name_plural%` - Currency name (plural)
- `%pulse_currency_symbol%` - Currency symbol

### Server Statistics
- `%pulse_players_total%` - Total players in database
- `%pulse_players_online%` - Current online players
- `%pulse_ranks_total%` - Total ranks
- `%pulse_default_rank%` - Default rank ID

### Time
- `%pulse_last_seen%` - Time since player was last seen

### Dynamic Placeholders
- `%pulse_has_permission_<permission>%` - Check if player has permission (true/false)
- `%pulse_rank_players_online_<rank>%` - Online players with specific rank
- `%pulse_rank_players_total_<rank>%` - Total players with specific rank
- `%pulse_rank_weight_<rank>%` - Weight of specific rank
- `%pulse_rank_prefix_<rank>%` - Prefix of specific rank
- `%pulse_rank_suffix_<rank>%` - Suffix of specific rank
- `%pulse_rank_display_<rank>%` - Display name of specific rank
- `%pulse_has_balance_<amount>%` - Check if player has balance amount (true/false)
- `%pulse_balance_formatted_<amount>%` - Format a specific amount with currency symbol

</details>
