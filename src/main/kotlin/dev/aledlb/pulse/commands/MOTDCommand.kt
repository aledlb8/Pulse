package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender

class MOTDCommand : BaseCommand() {
    override val name = "motd"
    override val permission = "pulse.motd"
    override val description = "Manage MOTD settings"
    override val usage = "/motd <reload|maintenance|toggle>"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sendUsage(sender)
            sender.sendMessage(
                Component.text("Available subcommands:", NamedTextColor.YELLOW)
                    .append(Component.newline())
                    .append(Component.text("  /motd reload", NamedTextColor.GRAY))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Reload MOTD configuration", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("  /motd maintenance <on|off|toggle>", NamedTextColor.GRAY))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Toggle maintenance mode", NamedTextColor.WHITE))
            )
            return
        }

        when (args[0].lowercase()) {
            "reload" -> {
                if (!requirePermission(sender, "pulse.motd.reload")) return

                Pulse.getPlugin().motdManager.reload()
                sender.sendMessage(
                    Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(Component.text("MOTD configuration reloaded!", NamedTextColor.GREEN))
                )
            }

            "maintenance" -> {
                if (!requirePermission(sender, "pulse.motd.maintenance")) return

                if (args.size < 2) {
                    sender.sendMessage(
                        Component.text("Usage: ", NamedTextColor.RED)
                            .append(Component.text("/motd maintenance <on|off|toggle>", NamedTextColor.GRAY))
                    )
                    return
                }

                val motdManager = Pulse.getPlugin().motdManager

                when (args[1].lowercase()) {
                    "on", "enable", "true" -> {
                        motdManager.setMaintenanceMode(true)
                        sender.sendMessage(
                            Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text("Maintenance mode ", NamedTextColor.GREEN))
                                .append(Component.text("enabled", NamedTextColor.YELLOW, TextDecoration.BOLD))
                        )
                    }

                    "off", "disable", "false" -> {
                        motdManager.setMaintenanceMode(false)
                        sender.sendMessage(
                            Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text("Maintenance mode ", NamedTextColor.GREEN))
                                .append(Component.text("disabled", NamedTextColor.RED, TextDecoration.BOLD))
                        )
                    }

                    "toggle" -> {
                        val enabled = motdManager.toggleMaintenanceMode()
                        sender.sendMessage(
                            Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text("Maintenance mode ", NamedTextColor.GREEN))
                                .append(
                                    Component.text(
                                        if (enabled) "enabled" else "disabled",
                                        if (enabled) NamedTextColor.YELLOW else NamedTextColor.RED,
                                        TextDecoration.BOLD
                                    )
                                )
                        )
                    }

                    else -> {
                        sender.sendMessage(
                            Component.text("Invalid option! ", NamedTextColor.RED)
                                .append(Component.text("Use: on, off, or toggle", NamedTextColor.GRAY))
                        )
                    }
                }
            }

            else -> {
                sendUsage(sender)
            }
        }
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("reload", "maintenance")
                .filter { it.startsWith(args[0].lowercase()) }

            2 -> {
                if (args[0].lowercase() == "maintenance") {
                    listOf("on", "off", "toggle")
                        .filter { it.startsWith(args[1].lowercase()) }
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
    }
}
