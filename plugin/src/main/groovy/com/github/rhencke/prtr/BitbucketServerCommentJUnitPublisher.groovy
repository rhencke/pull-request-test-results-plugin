package com.github.rhencke.prtr

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import hudson.Extension
import hudson.model.Descriptor
import hudson.tasks.junit.TestDataPublisher

/**
 * When a JUnit report is published with failing tests from a pull request
 * build, comment directly on the pull request with the details, as close
 * to the source of the failed test as possible.
 */
@CompileStatic
@TupleConstructor
@DataBound
class BitbucketServerCommentJUnitPublisher extends TestDataPublisher {

    @Extension
    static class DescriptorImpl extends Descriptor<TestDataPublisher> {
        final String displayName = 'Report Failures on Pull Request'
    }
}
