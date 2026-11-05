package com.musman.kompression

sealed class CompressState {
    data object Idle : CompressState()
    data object Working : CompressState()
    class Done(val summary: String, val preview: ByteArray) : CompressState()
    class Failed(val message: String) : CompressState()
}
