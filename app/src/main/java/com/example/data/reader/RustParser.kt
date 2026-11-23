package com.example.data.reader

object RustParser {
    init {
        // Load the native library produced by Rust (libkismis_core.so)
        System.loadLibrary("kismis_core")
    }

    external fun parseEpub(data: ByteArray): String
    external fun renderPdfPage(data: ByteArray, page: Int): ByteArray
}
