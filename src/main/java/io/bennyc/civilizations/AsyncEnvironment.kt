/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AsyncEnvironment {
    fun run(runnable: suspend () -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            runnable()
        }
    }
}