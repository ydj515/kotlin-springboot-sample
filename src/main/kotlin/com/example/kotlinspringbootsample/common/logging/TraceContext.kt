package com.example.kotlinspringbootsample.common.logging

object TraceContext {
    const val TRACE_ID_KEY = "traceId"
    const val REQUEST_ID_KEY = "requestId"

    const val TRACE_ID_HEADER = "X-Trace-Id"
    const val REQUEST_ID_HEADER = "X-Request-Id"
}
