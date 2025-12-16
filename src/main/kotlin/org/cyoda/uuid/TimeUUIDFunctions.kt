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
import java.nio.ByteBuffer
import java.util.*


/*
 * The min and max possible lsb for a UUID.
 * Note that his is not 0 and all 1's because Cassandra TimeUUIDType
 * compares the lsb parts as a signed byte array comparison. So the min
 * value is 8 times -128 and the max is 8 times +127.
 *<p>
 * @see org.apache.cassandra.utils.UUIDGen
 */
private const val MIN_CLOCK_SEQ_AND_NODE = -0x7f7f7f7f7f7f7f80L
private const val MAX_CLOCK_SEQ_AND_NODE = 0x7f7f7f7f7f7f7f7fL

private const val NUM_100NS_INTERVALS_SINCE_UUID_EPOCH = 0x01b21dd213814000L

private const val MIN_TIME = 0x0000000000001000L
val minTimeUuid: UUID = UUID(MIN_TIME, MIN_CLOCK_SEQ_AND_NODE)

private const val MAX_TIME = -0xe001L
val maxTimeUuid: UUID = UUID(MAX_TIME, MAX_CLOCK_SEQ_AND_NODE)

/**
 * Gets a new and unique time uuid in milliseconds. It is useful to use in a
 * TimeUUIDType sorted column family.
 *<p>
 * @return the time uuid
 */
fun uniqueTimeUUIDinMillis() = UUID(UUIDGen.newTime(), UUIDGen.getClockSeqAndNode())

fun getMillisTimeUUID(millis: Long) = UUID(createTimeFromMillis(millis), UUIDGen.getClockSeqAndNode())

fun getMillisTimeUUID(millis: Long, leastSigBits: Long) = UUID(createTimeFromMillis(millis), leastSigBits)

fun getMicrosTimeUUID(micros: Long) = UUID(createTimeFromMicros(micros), UUIDGen.getClockSeqAndNode())

fun getMicrosTimeUUID(micros: Long, leastSigBits: Long) = UUID(createTimeFromMicros(micros), leastSigBits)

fun getMinMicrosTimeUUID(micros: Long) = UUID(createTimeFromMicros(micros), MIN_CLOCK_SEQ_AND_NODE)

fun getMaxMicrosTimeUUID(micros: Long) = UUID(createTimeFromMicros(micros), MAX_CLOCK_SEQ_AND_NODE)

fun getMinTimeUUID(uuid: UUID) = UUID(uuid.mostSignificantBits, MIN_CLOCK_SEQ_AND_NODE)

fun getMaxTimeUUID(uuid: UUID) = UUID(uuid.mostSignificantBits, MAX_CLOCK_SEQ_AND_NODE)

internal fun createTimeFromMillis(millis: Long): Long {
    var time: Long

    // UTC time
    val timeToUse = (millis * 10000) + NUM_100NS_INTERVALS_SINCE_UUID_EPOCH

    // time low
    time = timeToUse shl 32

    // time mid
    time = time or ((timeToUse and 0xFFFF00000000L) shr 16)

    // time hi and version
    time = time or (0x1000L or ((timeToUse shr 48) and 0x0FFFL)) // version 1
    return time
}

internal fun createTimeFromMicros(micros: Long): Long {
    var time: Long

    // UTC time
    val timeToUse: Long = (micros * 10) + NUM_100NS_INTERVALS_SINCE_UUID_EPOCH

    // time low
    time = timeToUse shl 32

    // time mid
    time = time or ((timeToUse and 0xFFFF00000000L) shr 16)

    // time hi and version
    time = time or (0x1000L or ((timeToUse shr 48) and 0x0FFFL)) // version 1
    return time
}


/**
 * Returns an instance of uuid. Useful for when you read out of cassandra
 * you are getting a byte[] that needs to be converted into a TimeUUID.
 *<p>
 * @param uuid
 * the uuid
 * @return the UUID
 */
fun toUUID(uuid: ByteArray?) = uuid(uuid, 0)

/**
 * Retrieves the time as long based on the byte[] representation of a UUID.
 *<p>
 * @param uuid
 * byte[] uuid representation
 * @return a long representing the time
 */
fun getMillisTimeFromUUID(uuid: ByteArray?) = getMillisTimeFromUUID(toUUID(uuid))

fun getMillisTimeFromUUID(uuid: UUID) = (uuid.timestamp() - NUM_100NS_INTERVALS_SINCE_UUID_EPOCH) / 10000

fun getMicrosTimeFromUUID(uuid: UUID) = (uuid.timestamp() - NUM_100NS_INTERVALS_SINCE_UUID_EPOCH) / 10

fun convertToTimeUuid(uuid: UUID) =
    UUID((uuid.mostSignificantBits or 0x1000L) and 0xE000L.inv(), uuid.leastSignificantBits)

/**
 * As byte array.
 *<p>
 * @param uuid
 * the uuid
 *<p>
 * @return the byte[]
 */
fun asByteArray(uuid: UUID): ByteArray {
    val msb = uuid.mostSignificantBits
    val lsb = uuid.leastSignificantBits
    val buffer = ByteArray(16)

    for (i in 0..7) {
        buffer[i] = (msb ushr 8 * (7 - i)).toByte()
    }

    for (i in 8..15) {
        buffer[i] = (lsb ushr 8 * (7 - i)).toByte()
    }

    return buffer
}

/**
 * Coverts a UUID into a ByteBuffer.
 *<p>
 * @param uuid
 * a UUID
 * @return a ByteBuffer representaion of the param UUID
 */
fun asByteBuffer(uuid: UUID?): ByteBuffer? {
    if (uuid == null) {
        return null
    }

    return ByteBuffer.wrap(asByteArray(uuid))
}

fun uuid(uuid: ByteArray?, offset: Int): UUID {
    val bb = ByteBuffer.wrap(uuid, offset, 16)
    return UUID(bb.getLong(), bb.getLong())
}

/**
 * Converts a ByteBuffer containing a UUID into a UUID
 *<p>
 * @param bb
 * a ByteBuffer containing a UUID
 * @return a UUID
 */
fun uuid(bb: ByteBuffer): UUID {
    var bb = bb
    bb = bb.slice()
    return UUID(bb.getLong(), bb.getLong())
}

