package com.github.rhencke.prtr

import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import org.testng.asserts.SoftAssert
import java.io.File
import java.nio.file.Files

class BitbucketServerCommentJUnitPublisherTest {
    private var b = BitbucketServerCommentJUnitPublisher()
    private var mockJob = MockJob()
    private var mockRun = MockRun(mockJob)
    private var tempWs = FilePath(File("."))
    private var assert = SoftAssert()

    @BeforeTest fun setup() {
        b = BitbucketServerCommentJUnitPublisher()
        mockJob = MockJob()
        mockRun = MockRun(mockJob)
        tempWs = FilePath(Files.createTempDirectory("tests-").toFile())
        assert = SoftAssert()
    }

    @AfterTest fun tearDown() {
        tempWs.deleteRecursive()
        assert.assertAll()
    }

    @Test fun doesNotAlterTestData() {
        val ret = b.contributeTestData(mockRun, tempWs, mockLauncher, mockListener, mockTestResult)
        assert.assertNull(ret)
    }
}