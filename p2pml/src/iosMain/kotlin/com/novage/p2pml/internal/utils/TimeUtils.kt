package com.novage.p2pml.internal.utils

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun getCurrentEpochSeconds(): Double = NSDate().timeIntervalSince1970
