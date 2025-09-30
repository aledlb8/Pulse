package dev.aledlb.pulse.ranks.models

data class Rank(
    val name: String,
    val prefix: String,
    val suffix: String,
    val permissions: MutableSet<String>,
    val weight: Int,
    val isDefault: Boolean = false,
    val parents: MutableSet<String> = mutableSetOf()
) {
    fun hasPermission(permission: String): Boolean {
        if (permissions.contains("*")) return true
        if (permissions.contains(permission)) return true

        // Check for wildcard permissions
        val parts = permission.split(".")
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (permissions.contains(wildcard)) return true
        }

        return false
    }

    fun addPermission(permission: String) {
        permissions.add(permission)
    }

    fun removePermission(permission: String) {
        permissions.remove(permission)
    }

    fun addParent(parentName: String) {
        parents.add(parentName)
    }

    fun removeParent(parentName: String) {
        parents.remove(parentName)
    }

    fun copy(): Rank {
        return Rank(
            name = name,
            prefix = prefix,
            suffix = suffix,
            permissions = permissions.toMutableSet(),
            weight = weight,
            isDefault = isDefault,
            parents = parents.toMutableSet()
        )
    }
}