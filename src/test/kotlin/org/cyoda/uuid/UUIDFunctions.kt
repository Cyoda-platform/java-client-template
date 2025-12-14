/*
 * Copyright (c) 2024 Cyoda Limited. All rights reserved.
 * This software is the confidential and proprietary information of Cyoda Limited ("Confidential Information").
 * Unauthorized use, disclosure, distribution, or reproduction is prohibited. Any use or access to this software
 * is subject to the terms of the applicable agreements and prior written consent from Cyoda Limited.
 */

package org.cyoda.uuid

import java.util.*

fun UUID.toTimeUUID() =
    UUID((this.mostSignificantBits or 0x1000L) and 0xE000L.inv(), this.leastSignificantBits)
