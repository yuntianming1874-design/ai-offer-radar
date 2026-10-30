package com.aioffer.radar.core.model

enum class PlanType(val isPaid: Boolean) {
    STANDARD(true),
    STUDENT(true),
    FAMILY(true),
    TEAM(true),
    FREE(false),
    FREE_TRIAL(false)
}
