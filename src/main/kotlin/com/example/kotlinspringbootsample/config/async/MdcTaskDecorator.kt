package com.example.kotlinspringbootsample.config.async

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator

class MdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val callerContext = MDC.getCopyOfContextMap()

        return Runnable {
            val workerContext = MDC.getCopyOfContextMap()
            try {
                if (callerContext != null) {
                    MDC.setContextMap(callerContext)
                } else {
                    MDC.clear()
                }
                runnable.run()
            } finally {
                if (workerContext != null) {
                    MDC.setContextMap(workerContext)
                } else {
                    MDC.clear()
                }
            }
        }
    }
}
