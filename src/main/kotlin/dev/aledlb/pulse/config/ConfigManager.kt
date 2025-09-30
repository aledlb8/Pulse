package dev.aledlb.pulse.config

import dev.aledlb.pulse.util.Logger
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File
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
            configNodes[fileName] = node
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

}