package com.github.rhencke.prtr

import java.util.*

class MockJob : Job<MockJob, MockRun>(null, null) {
    override fun removeRun(p0: MockRun) = Unit
    override fun _getRuns(): SortedMap<Int, out MockRun> = sortedMapOf()
    override fun isBuildable(): Boolean = true
}