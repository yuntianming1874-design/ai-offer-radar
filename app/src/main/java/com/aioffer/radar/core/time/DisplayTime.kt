package com.aioffer.radar.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * All user-facing timestamps are shown in China Standard Time.
 *
 * The database stores UTC instants; only the presentation layer converts.
 * The zone is deliberately fixed so the same offer reads identically on a
 * device configured for another region.
 */
object DisplayTime {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(zone)

    fun format(instant: Instant): String = "${formatter.format(instant)} 北京时间"
}
