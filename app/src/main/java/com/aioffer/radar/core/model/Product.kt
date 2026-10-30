package com.aioffer.radar.core.model

data class Product(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val isDemo: Boolean
)
