package com.github.rhencke.prtr

import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Descriptor
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tasks.junit.TestDataPublisher
import hudson.tasks.junit.TestResult
import hudson.tasks.junit.TestResultAction
import org.kohsuke.stapler.DataBoundConstructor

class BitbucketServerCommentJUnitPublisher @DataBoundConstructor constructor() : TestDataPublisher() {

    override fun contributeTestData(run: Run<*, *>, workspace: FilePath, launcher: Launcher, listener: TaskListener, testResult: TestResult): TestResultAction.Data? {
        return null
    }

    @Extension class DescriptorImpl : Descriptor<TestDataPublisher>() {
        override fun getDisplayName() = "Comment Publisher Thing"
    }
}