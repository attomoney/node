package atto.node

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.Duration


abstract class AsynchronousQueueProcessor<T>(private val duration: Duration) {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var running = false

    @PostConstruct
    fun start() {
        running = true
        val className = this.javaClass.simpleName
        CoroutineScope(Dispatchers.Default + attoCoroutineExceptionHandler).launch {
            while (running) {
                process()
                delay(duration.inWholeMilliseconds)
            }
            logger.info { "Stopped $className" }
        }
    }

    @PreDestroy
    open fun stop() {
        running = false
    }

    protected abstract suspend fun poll(): T?

    protected abstract suspend fun process(value: T)

    private suspend fun process() {
        var value = poll()
        while (value != null) {
            process(value)
            value = poll()
        }
    }

}