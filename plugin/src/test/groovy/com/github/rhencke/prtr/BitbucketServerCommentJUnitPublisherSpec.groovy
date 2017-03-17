package com.github.rhencke.prtr

import spock.lang.Specification

class BitbucketServerCommentJUnitPublisherSpec extends Specification {
    def 'can create instance'() {
        when:
        new BitbucketServerCommentJUnitPublisher()

        then:
        notThrown(Throwable)
    }
}
