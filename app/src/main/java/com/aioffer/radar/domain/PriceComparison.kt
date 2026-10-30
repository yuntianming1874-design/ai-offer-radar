package com.aioffer.radar.domain

import com.aioffer.radar.core.model.ComparisonKey

fun ComparisonKey.canCompareWith(other: ComparisonKey): Boolean = this == other
