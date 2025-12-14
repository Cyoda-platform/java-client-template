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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class SimpleSystemClockTest {

    val systemClock = SimpleSystemClock
    @Test
    fun testUniqueMicroTime() {
        val time1 = systemClock.uniqueMicroTime()
        val time2 = systemClock.uniqueMicroTime()

        // Test that the times are unique
        assertTrue(time1 != time2) {"The times should be unique"}

        // Test that the times are monotonically increasing
        assertTrue(time2 > time1){"The second time should be greater than the first"}
    }

    @Test
    fun testConcurrentCalls() {
        val numberOfThreads = 100
        val times = LongArray(numberOfThreads)
        val threads = Array(numberOfThreads) { i ->
            thread(start = true) {
                times[i] = systemClock.uniqueMicroTime()
            }
        }

        threads.forEach { it.join() }

        // Check if all times are unique
        for (i in times.indices) {
            for (j in i + 1 until times.size) {
                assertTrue(times[i] != times[j]){"Times should be unique across threads"}
            }
        }
    }
}