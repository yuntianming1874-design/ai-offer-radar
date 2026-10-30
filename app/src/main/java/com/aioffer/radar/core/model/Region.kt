package com.aioffer.radar.core.model

@JvmInline
value class Region(val code: String) {
    companion object {
        val MAINLAND_CHINA = Region("CN")
    }
}
