# ✨ Pulse Features

## 🎯 Core Systems

### 🏆 Rank System
A comprehensive rank management system with support for multiple ranks per player, temporary ranks, and advanced permission inheritance.

**Features:**
- Create unlimited custom ranks with weights, prefixes, and suffixes
- Assign multiple ranks to a single player simultaneously
- Set temporary ranks with automatic expiration
- Primary rank system with automatic weight-based selection
- Default rank assignment for new players
- Full permission inheritance from ranks to players

**Commands:**
- `/rank create <rank> <weight> <prefix> <suffix>` - Create a new rank
- `/rank delete <rank>` - Delete an existing rank
- `/rank set <player> <rank> [duration]` - Set a player's rank
- `/rank info <rank>` - View detailed rank information
- `/rank list` - List all available ranks
- `/rank permission <rank> <add|remove|deny|list> [permission]` - Manage rank permissions
- `/rank reload` - Reload the rank system

### 🎁 Grant System (GUI)
An intuitive GUI-based system for managing player ranks with visual feedback and confirmation steps.

**Features:**
- Clean, user-friendly inventory interface
- Grant ranks with custom durations (1h, 1d, 7d, 30d, 90d, or permanent)
- View all active ranks on a player
- Remove ranks with a single click
- Visual distinction between primary and secondary ranks
- Real-time expiration countdown display
- Confirmation system to prevent accidental grants

**Usage:**
- `/grant <player>` - Open the grant GUI for a player
- Navigate through menus to select ranks and durations
- Click to confirm or cancel operations

### 🔐 Permission System
Advanced permission management with player-specific and rank-based permissions, including denial support.

**Features:**
- Add permissions directly to players or ranks
- Deny specific permissions (overrides grants)
- Permission inheritance from all player ranks
- Check player permissions with full inheritance resolution
- List all permissions for players or ranks
- Real-time permission updates without reconnection

**Commands:**
- `/permission add <player> <permission>` - Add a permission to a player
- `/permission remove <player> <permission>` - Remove a permission from a player
- `/permission deny <player> <permission>` - Deny a permission for a player
- `/permission check <player> <permission>` - Check if a player has a permission
- `/permission list <player>` - List all player permissions

### 💰 Economy System
Full-featured economy with Vault integration, transaction history, and leaderboards.

**Features:**
- Customizable currency name and symbol
- Support for decimal balances
- Player-to-player payments with transaction fees
- Balance leaderboards (top balances)
- Admin commands for adding, removing, and setting balances
- Vault economy provider for compatibility with other plugins
- Persistent storage with automatic save

**Commands:**
- `/coin` - Check your balance
- `/coin <player>` - Check another player's balance
- `/coin pay <player> <amount>` - Pay another player
- `/coin add <player> <amount>` - Add coins to a player (admin)
- `/coin remove <player> <amount>` - Remove coins from a player (admin)
- `/coin set <player> <amount>` - Set a player's balance (admin)
- `/coin top` - View balance leaderboard

### 🛒 Shop System
YAML-configured shop with multi-page support, enchantments, and flexible pricing.

**Features:**
- Multiple shop pages with custom titles
- Support for items with custom names, lore, and enchantments
- Stack-based purchasing (buy multiple items at once)
- Permission-based shop access
- Economy integration for purchases
- Live configuration reload without restart
- Potion effects and item flags support

**Commands:**
- `/shop` - Open the shop GUI
- `/shop list` - List all shop items
- `/shop reload` - Reload shop configuration (admin)

**Configuration:**
```yaml
pages:
  - title: "Main Shop"
    items:
      - slot: 0
        material: DIAMOND_SWORD
        name: "&bDiamond Sword"
        price: 1000
        enchantments:
          - SHARPNESS:5
```

### 🏷️ Tag System
Player name tags with prefixes, suffixes, colors, and decorations.

**Features:**
- Create unlimited custom tags
- Rich formatting with colors, gradients, and decorations
- Tag collections and unlockables
- Give/remove tags from players
- Players can equip tags from their collection
- Permission-based tag access
- Default tag support

**Commands:**
- `/tag` - Open tag selection GUI
- `/tag <tag>` - Equip a specific tag
- `/tag others <player>` - View another player's tags (admin)
- `/tag give <player> <tag>` - Give a tag to a player (admin)
- `/tag remove <player> <tag>` - Remove a tag from a player (admin)
- `/tag create <id> <prefix> <suffix>` - Create a new tag (admin)
- `/tag edit <id> <prefix|suffix> <value>` - Edit a tag (admin)
- `/tag delete <id>` - Delete a tag (admin)
- `/tag reload` - Reload tag system (admin)

### ⚖️ Punishment System
Comprehensive moderation toolkit with temporary and permanent punishments.

**Features:**
- Kick, warn, mute, freeze, ban, and IP ban
- Temporary bans and IP bans with duration support
- Warning accumulation system with history
- Remove individual warnings
- Persistent punishment storage
- Broadcast messages for public punishments
- Staff-only silent punishments

**Commands:**
- `/kick <player> [reason]` - Kick a player
- `/warn <player> <reason>` - Warn a player
- `/warns <player>` - View player warnings
- `/unwarn <player> <id>` - Remove a warning
- `/mute <player> [duration] [reason]` - Mute a player
- `/unmute <player>` - Unmute a player
- `/freeze <player>` - Freeze/unfreeze a player
- `/ban <player> [reason]` - Permanently ban a player
- `/tempban <player> <duration> [reason]` - Temporarily ban a player
- `/unban <player>` - Unban a player
- `/ipban <player> [reason]` - IP ban a player
- `/tempipban <player> <duration> [reason]` - Temporarily IP ban a player

### 🎮 Gamemode Commands
Quick gamemode switching with shorthand aliases and permission support.

**Features:**
- Change your own gamemode or others' with permission
- Short aliases for quick switching (gmc, gms, gma, gmsp)
- Permission-based access control per gamemode

**Commands:**
- `/gamemode <mode> [player]` - Change gamemode
- `/gmc [player]` - Creative mode
- `/gms [player]` - Survival mode
- `/gma [player]` - Adventure mode
- `/gmsp [player]` - Spectator mode

### 💬 Chat System
Advanced chat formatting with rank integration and color support.

**Features:**
- Automatic rank prefix/suffix in chat
- Color code support for players with permission
- PlaceholderAPI integration for chat format
- Configurable chat format via YAML
- Persistent display name formatting

## 🔧 Technical Features

### 🗄️ Database
- **Exposed ORM** with Kotlin DSL for type-safe queries
- **HikariCP** connection pooling for optimal performance
- Support for **SQLite** (default) and **MySQL/MariaDB**
- Automatic schema creation and migration
- Asynchronous database operations (non-blocking)
- Connection validation and automatic recovery

### ⚡ Performance
- **Folia-compatible** with proper thread scheduling
- **Async-first architecture** for all heavy operations
- **Caching layer** for frequently accessed data
- Optimized database queries with prepared statements
- Minimal main thread blocking
- Efficient placeholder resolution

### 🔌 Integration
- **Vault integration** for economy and permissions
- **PlaceholderAPI expansion** with 40+ placeholders
- **LuckPerms compatibility** (optional, Pulse can standalone)
- Event-driven architecture for extensibility
- Open API for third-party plugins

### 📝 Configuration
- **YAML-based** configuration files
- **Automatic resource versioning** with migration
- **Hot-reload support** for most configurations
- **Validation** with helpful error messages
- Per-system configuration files for organization

### 🎨 Customization
- **Messages.yml** for all player-facing text
- Support for **MiniMessage** and legacy color codes
- **Configurable prefixes** and suffixes
- Custom **shop layouts** with unlimited pages
- **Punishment reasons** and duration formats

### 🛡️ Security
- **SQL injection protection** via prepared statements
- **Permission checks** on all sensitive commands
- **Input validation** and sanitization
- **Rate limiting** on economy transactions
- Audit trail for administrative actions

## 🌐 Multi-Server Support
- Shared database for cross-server data
- Synchronized rank changes across network
- Global economy balances
- Network-wide punishments
- Cross-server placeholders

## 📦 Modular Architecture
Every system is independently functional:
- Disable economy if using another plugin
- Run without shop if not needed
- Use only the rank system
- Mix and match features as needed

---

**Pulse** - One plugin, infinite possibilities. Built for performance, designed for simplicity.
