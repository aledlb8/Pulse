package dev.aledlb.pulse.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Helper object for asynchronous database operations.
 * Provides consistent error handling and logging.
 */
object AsyncHelper {
    
    /**
     * Execute a database operation asynchronously with error handling
     * 
     * @param operation The operation to execute
     * @param errorMessage The error message to log if the operation fails
     * @param onError Optional callback to execute on error
     */
    fun executeAsync(
        operation: suspend () -> Unit,
        errorMessage: String,
        onError: ((Exception) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                operation()
            } catch (e: Exception) {
                Logger.error(errorMessage, e)
                onError?.invoke(e)
            }
        }
    }
    
    /**
     * Execute a database save operation asynchronously
     * 
     * @param entityName The name of the entity being saved (for logging)
     * @param operation The save operation to execute
     */
    fun saveAsync(entityName: String, operation: suspend () -> Unit) {
        executeAsync(
            operation = operation,
            errorMessage = "Failed to save $entityName to database"
        )
    }
    
    /**
     * Execute a database load operation asynchronously
     * 
     * @param entityName The name of the entity being loaded (for logging)
     * @param operation The load operation to execute
     * @param onSuccess Callback to execute with the loaded data
     * @param onError Optional callback to execute on error
     */
    fun <T> loadAsync(
        entityName: String,
        operation: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = operation()
                onSuccess(result)
            } catch (e: Exception) {
                Logger.error("Failed to load $entityName from database", e)
                onError?.invoke(e)
            }
        }
    }
}
