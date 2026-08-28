package com.eventtracker.sdk.internal.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Форматирует timestamp в Unix-миллисекундах в строку-ведро `DD/MM/YYYY`, которая используется
 * как значение сохраняемой колонки `createdDate` (см. требование схемы) и для группировки в
 * статистике.
 *
 * Используется фиксированная [Locale.US], чтобы разделитель/порядок никогда не зависели от
 * локали устройства, но при этом дефолтная [ZoneId] устройства — чтобы "сегодня" совпадало с
 * тем, что пользователь реально воспринимает как сегодня, а не с UTC-днём, который может
 * отличаться на день в зависимости от часового пояса.
 *
 * [DateTimeFormatter] неизменяем и потокобезопасен, поэтому вызывать это можно конкурентно из
 * нескольких потоков без какой-либо внешней синхронизации.
 */
internal object DateFormatting {

    private val pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

    fun dayBucket(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(pattern)
}
