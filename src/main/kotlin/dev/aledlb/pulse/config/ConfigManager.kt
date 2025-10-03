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
        val isNewFile = !configFile.exists()

        if (isNewFile && createDefault) {
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

            // Only merge defaults for brand new files, not existing customized configs
            if (isNewFile && createDefault) {
                mergeDefaultsFromResource(fileName, node)
            }

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
        val configFile = File(dataFolder, fileName)
        
        if (!configFile.exists()) {
            return loadConfig(fileName, false)
        }

        val loader = configs[fileName] ?: YamlConfigurationLoader.builder()
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
            Logger.error("Failed to reload configuration: $fileName", e)
            loader.createNode()
        }
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
     * without overriding existing user values or removing comments. Returns true if any changes were applied.
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

        // Find missing keys by comparing the structures
        val missingKeys = mutableListOf<String>()
        
        fun findMissingKeys(source: ConfigurationNode, target: ConfigurationNode, path: String = "") {
            val sourceChildren = source.childrenMap()
            if (sourceChildren.isEmpty()) return

            for ((key, sourceChild) in sourceChildren) {
                val currentPath = if (path.isEmpty()) key.toString() else "$path.$key"
                val targetChild = target.node(key)
                
                if (targetChild.virtual()) {
                    // Key is missing - add to list
                    missingKeys.add(currentPath)
                } else if (!sourceChild.childrenMap().isEmpty() && !targetChild.childrenMap().isEmpty()) {
                    // Both are maps - recurse
                    findMissingKeys(sourceChild, targetChild, currentPath)
                }
            }
        }

        findMissingKeys(defaultNode, liveNode)

        if (missingKeys.isEmpty()) {
            return false
        }

        // If we have missing keys, we need to add them while preserving comments
        // We'll do this by manipulating the file as text
        return addMissingKeysToFile(fileName, defaultNode, missingKeys, liveNode)
    }

    /**
     * Add missing configuration keys to the file while preserving comments and structure
     */
    private fun addMissingKeysToFile(fileName: String, defaultNode: ConfigurationNode, missingKeys: List<String>, liveNode: ConfigurationNode): Boolean {
        val configFile = File(dataFolder, fileName)
        if (!configFile.exists()) return false

        try {
            // Read the current file content
            val currentContent = configFile.readText()
            val lines = currentContent.lines().toMutableList()

            // Read the default resource to get the full content with comments
            val resourceStream = this::class.java.classLoader.getResourceAsStream(fileName) ?: return false
            val defaultContent = resourceStream.bufferedReader().use { it.readText() }
            val defaultLines = defaultContent.lines()

            // For each missing key, find it in the default content and add it
            for (missingKey in missingKeys) {
                val keyParts = missingKey.split(".")
                val addedLines = extractKeyLinesFromDefault(defaultLines, keyParts)
                
                if (addedLines.isNotEmpty()) {
                    // Find the best place to insert these lines
                    val insertIndex = findInsertionPoint(lines, keyParts)
                    lines.addAll(insertIndex, addedLines)
                }
            }

            // Write the updated content back to the file
            configFile.writeText(lines.joinToString("\n"))
            
            // Reload the configuration node to reflect the changes
            val loader = configs[fileName]
            if (loader != null) {
                val updatedNode = loader.load()
                configNodes[fileName] = updatedNode
                // Update the passed liveNode reference
                liveNode.childrenMap().keys.toList().forEach { liveNode.removeChild(it) }
                liveNode.mergeFrom(updatedNode)
            }

            return true
        } catch (e: Exception) {
            Logger.error("Failed to add missing keys to $fileName", e)
            return false
        }
    }

    /**
     * Extract the lines for a specific key path from the default configuration
     */
    private fun extractKeyLinesFromDefault(defaultLines: List<String>, keyParts: List<String>): List<String> {
        val result = mutableListOf<String>()
        var currentIndent = 0
        var foundKey = false
        var keyIndex = 0

        for (i in defaultLines.indices) {
            val line = defaultLines[i]
            val trimmed = line.trim()
            
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                if (foundKey && keyIndex == keyParts.size) {
                    result.add(line)
                }
                continue
            }

            val indent = line.length - line.trimStart().length
            val keyMatch = trimmed.split(":")[0].trim()

            if (keyIndex < keyParts.size && keyMatch == keyParts[keyIndex]) {
                if (keyIndex == 0) {
                    foundKey = true
                    currentIndent = indent
                }
                
                if (foundKey) {
                    result.add(line)
                }
                
                keyIndex++
                
                if (keyIndex == keyParts.size) {
                    // Found the complete key path, now collect its value and any comments
                    var j = i + 1
                    while (j < defaultLines.size) {
                        val nextLine = defaultLines[j]
                        val nextTrimmed = nextLine.trim()
                        val nextIndent = nextLine.length - nextLine.trimStart().length
                        
                        if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("#")) {
                            result.add(nextLine)
                        } else if (nextIndent > currentIndent) {
                            result.add(nextLine)
                        } else {
                            break
                        }
                        j++
                    }
                    break
                }
            } else if (foundKey && indent <= currentIndent && keyIndex < keyParts.size) {
                // We've moved to a different section, reset
                foundKey = false
                keyIndex = 0
                result.clear()
            }
        }

        return result
    }

    /**
     * Find the best place to insert new configuration lines
     */
    private fun findInsertionPoint(lines: MutableList<String>, keyParts: List<String>): Int {
        // Try to find a related section or insert at the end
        val topLevelKey = keyParts[0]
        
        // Look for the top-level key or similar keys
        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()
            
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val existingKey = trimmed.split(":")[0].trim()
                if (existingKey == topLevelKey) {
                    // Found existing section, don't duplicate
                    return i
                }
            }
        }
        
        // Insert at the end
        return lines.size
    }
}