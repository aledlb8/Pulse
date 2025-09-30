package dev.aledlb.pulse.tags.models

import org.bukkit.Material

data class Tag(
    val id: String,
    val name: String,
    val prefix: String = "",
    val suffix: String = "",
    val description: List<String> = emptyList(),
    val material: Material = Material.NAME_TAG,
    val price: Double = 0.0,
    val permission: String? = null,
    val enabled: Boolean = true,
    val purchasable: Boolean = false
) {
    fun getFormattedPrefix(): String {
        return prefix.replace('&', '§')
    }

    fun getFormattedSuffix(): String {
        return suffix.replace('&', '§')
    }

    fun getFormattedDescription(): List<String> {
        return description.map { it.replace('&', '§') }
    }
}