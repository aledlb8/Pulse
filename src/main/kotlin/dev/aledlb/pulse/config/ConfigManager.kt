package dev.aledlb.pulse.config

import dev.aledlb.pulse.util.Logger
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ConfigManager(private val dataFolder: File) {
    private val configs = mutableMapOf<String, YamlConfigurationLoader>()
    private val configNodes = mutableMapOf<String, ConfigurationNode>()

    init {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
    }

    fun loadConfig(fileName: String, createDefault: Boolean = true): ConfigurationNode {
        val configFile = File(dataFolder, fileName)

        if (!configFile.exists() && createDefault) {
            createDefaultConfig(fileName, configFile)
        }

        val loader = YamlConfigurationLoader.builder()
            .path(configFile.toPath())
            .indent(2)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions { opts ->
                opts.shouldCopyDefaults(false)
            }
            .build()

        configs[fileName] = loader

        return try {
            val node = loader.load()

            // Merge in any new defaults from the bundled resource without overwriting user values
            val changed = mergeDefaultsFromResource(fileName, node)

            configNodes[fileName] = node

            if (changed) {
                // Persist injected defaults so users see new options next startup
                try {
                    loader.save(node)
                } catch (e: Exception) {
                    Logger.error("Failed to write merged defaults for: $fileName", e)
                }
            }

            node
        } catch (e: Exception) {
            Logger.error("Failed to load configuration: $fileName", e)
            loader.createNode()
        }
    }

    fun saveConfig(fileName: String) {
        val loader = configs[fileName] ?: return
        val node = configNodes[fileName] ?: return

        try {
            loader.save(node)
        } catch (e: Exception) {
            Logger.error("Failed to save configuration: $fileName", e)
        }
    }

    fun saveAll() {
        configs.keys.forEach { saveConfig(it) }
    }

    fun getConfig(fileName: String): ConfigurationNode? {
        return configNodes[fileName]
    }

    fun reloadConfig(fileName: String): ConfigurationNode {
        return loadConfig(fileName, false)
    }

    fun reloadAllConfigs() {
        configs.keys.toList().forEach { fileName ->
            reloadConfig(fileName)
        }
    }

    private fun createDefaultConfig(fileName: String, configFile: File) {
        try {
            // Copy the default config from resources
            val resourceStream = this::class.java.classLoader.getResourceAsStream(fileName)
            if (resourceStream != null) {
                resourceStream.use { input ->
                    configFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                Logger.error("Could not find default $fileName in resources!")
            }
        } catch (e: Exception) {
            Logger.error("Failed to create default $fileName", e)
        }
    }

    /**
     * Load the bundled resource YAML and copy any missing keys into the live node
     * without overriding existing user values. Returns true if any changes were applied.
     */
    private fun mergeDefaultsFromResource(fileName: String, liveNode: ConfigurationNode): Boolean {
        val resourceStream = this::class.java.classLoader.getResourceAsStream(fileName) ?: return false

        val defaultLoader = YamlConfigurationLoader.builder()
            .source { BufferedReader(InputStreamReader(resourceStream)) }
            .indent(2)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions { opts -> opts.shouldCopyDefaults(false) }
            .build()

        val defaultNode = try {
            defaultLoader.load()
        } catch (e: Exception) {
            Logger.debug("Failed to load default resource for $fileName: ${e.message}")
            return false
        }

        var changed = false

        fun merge(source: ConfigurationNode, target: ConfigurationNode) {
            // If the source is a map-like node
            val children = source.childrenMap()
            if (children.isNotEmpty()) {
                for ((key, childSrc) in children) {
                    val childTgt = target.node(key)
                    if (childTgt.virtual()) {
                        // Missing in target: copy entire subtree
                        childTgt.set(childSrc)
                        changed = true
                    } else {
                        // Exists: recurse for nested maps, do not override scalars
                        if (childSrc.childrenMap().isNotEmpty()) {
                            merge(childSrc, childTgt)
                        }
                    }
                }
                return
            }

            // If it's a scalar or list and the target is missing, copy it
            if (target.virtual()) {
                target.set(source)
                changed = true
            }
        }

        merge(defaultNode, liveNode)
        return changed
    }
}