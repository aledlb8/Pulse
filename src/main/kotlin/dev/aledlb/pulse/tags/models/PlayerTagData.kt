package dev.aledlb.pulse.tags.models

import java.util.*

data class PlayerTagData(
    val uuid: UUID,
    val name: String,
    val ownedTags: MutableSet<String> = mutableSetOf(),
    val activeTags: MutableSet<String> = mutableSetOf()
) {
    fun hasTag(tagId: String): Boolean = ownedTags.contains(tagId)

    fun addTag(tagId: String) = ownedTags.add(tagId)

    fun removeTag(tagId: String): Boolean {
        val removed = ownedTags.remove(tagId)
        if (removed) {
            activeTags.remove(tagId) // Also remove from active if owned is removed
        }
        return removed
    }

    fun activateTag(tagId: String): Boolean {
        return if (hasTag(tagId)) {
            activeTags.add(tagId)
        } else {
            false
        }
    }

    fun deactivateTag(tagId: String): Boolean = activeTags.remove(tagId)

    fun isTagActive(tagId: String): Boolean = activeTags.contains(tagId)

    fun getActiveTagsList(): List<String> = activeTags.toList()

    fun getOwnedTagsList(): List<String> = ownedTags.toList()
}