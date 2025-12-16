/*
 * Copyright (C) 2024 Cyoda Limited
 *<p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *<p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 *<p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyoda.uuid

import com.eaio.uuid.UUIDGen
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max


object SimpleSystemClock : SystemClock {
    private val LAST_TIME = AtomicLong()
    private const val ONE_THOUSAND = 1000L

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun currentTimeMicros(): Long = currentTimeMillis() * ONE_THOUSAND

    override fun uniqueMicroTime(): Long {
        val micros = System.nanoTime() / 1000
        return LAST_TIME.updateAndGet { lastTime -> max(micros.toDouble(), (lastTime + 1).toDouble()).toLong() }
    }

    override fun currentTimeNanos(): Long = System.nanoTime()

    override fun currentLocalDatetime(): LocalDateTime = LocalDateTime.now()

    override fun uniqueTimeUUIDinMicros() = UUID(
        createTimeFromMicros(this.uniqueMicroTime()),
        UUIDGen.getClockSeqAndNode()
    )
}
