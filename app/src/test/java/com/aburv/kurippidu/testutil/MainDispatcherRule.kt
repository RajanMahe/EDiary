package com.aburv.kurippidu.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces Dispatchers.Main (used by viewModelScope) with a test dispatcher,
 * so delays like the 800 ms autosave run on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // A background thread may still be finishing a resume on Main; retry briefly
        repeat(20) {
            try {
                Dispatchers.resetMain()
                return
            } catch (e: IllegalStateException) {
                Thread.sleep(25)
            }
        }
        Dispatchers.resetMain()
    }
}
