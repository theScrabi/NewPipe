package org.schabi.newpipe.testUtil

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Always run on [Schedulers.trampoline].
 * This executes the task in the current thread in FIFO manner.
 * This ensures that tasks are run quickly inside the tests
 * and not scheduled away to another thread for later execution
 */
class TrampolineSchedulerRule : TestRule {

    private val scheduler = Schedulers.trampoline()

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                    RxJavaPlugins.setIoSchedulerHandler { scheduler }
                    RxJavaPlugins.setNewThreadSchedulerHandler { scheduler }
                    RxJavaPlugins.setSingleSchedulerHandler { scheduler }
                    RxAndroidPlugins.setInitMainThreadSchedulerHandler { scheduler }

                    base.evaluate()
                } finally {
                    RxJavaPlugins.reset()
                    RxAndroidPlugins.reset()
                }
            }
        }
}
