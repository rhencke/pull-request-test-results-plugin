package com.github.rhencke.prtr

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotate the target class's longest tuple constructor as <annotation>@DataBoundConstructor</annotation>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass(classes = [DataBoundASTTransformation])
@interface DataBound {
}
