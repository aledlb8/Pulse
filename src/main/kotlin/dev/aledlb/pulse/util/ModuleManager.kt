package dev.aledlb.pulse.util

abstract class Module {
    abstract val name: String
    abstract val version: String
    protected var enabled = false

    open fun onEnable() {
        enabled = true
        Logger.success("Module '$name' enabled")
    }

    open fun onDisable() {
        enabled = false
        Logger.info("Module '$name' disabled")
    }

    open fun onReload() {
        Logger.info("Module '$name' reloaded")
    }

    fun isEnabled(): Boolean = enabled
}

object ModuleManager {
    private val modules = mutableMapOf<String, Module>()

    fun registerModule(module: Module) {
        modules[module.name.lowercase()] = module
        Logger.debug("Registered module: ${module.name}")
    }

    fun enableModule(name: String): Boolean {
        val module = modules[name.lowercase()]
        return if (module != null && !module.isEnabled()) {
            try {
                module.onEnable()
                true
            } catch (e: Exception) {
                Logger.error("Failed to enable module '$name'", e)
                false
            }
        } else {
            false
        }
    }

    fun disableModule(name: String): Boolean {
        val module = modules[name.lowercase()]
        return if (module != null && module.isEnabled()) {
            try {
                module.onDisable()
                true
            } catch (e: Exception) {
                Logger.error("Failed to disable module '$name'", e)
                false
            }
        } else {
            false
        }
    }

    fun reloadModule(name: String): Boolean {
        val module = modules[name.lowercase()]
        return if (module != null) {
            try {
                module.onReload()
                true
            } catch (e: Exception) {
                Logger.error("Failed to reload module '$name'", e)
                false
            }
        } else {
            false
        }
    }

    fun enableAll() {
        modules.values.forEach { module ->
            if (!module.isEnabled()) {
                enableModule(module.name)
            }
        }
    }

    fun disableAll() {
        modules.values.forEach { module ->
            if (module.isEnabled()) {
                disableModule(module.name)
            }
        }
    }

    fun getModule(name: String): Module? {
        return modules[name.lowercase()]
    }

    fun getModules(): Collection<Module> {
        return modules.values
    }

    fun getEnabledModules(): List<Module> {
        return modules.values.filter { it.isEnabled() }
    }
}