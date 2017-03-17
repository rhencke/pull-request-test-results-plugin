package com.github.rhencke.prtr

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.kohsuke.stapler.DataBoundConstructor

/**
 * Annotate the target class's longest tuple constructor as <annotation>@DataBoundConstructor</annotation>
 */
@GroovyASTTransformation
class DataBoundASTTransformation extends AbstractASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source)
        ClassNode parent = nodes[1] as ClassNode

        if (parent == null || parent.isInterface()) {
            addError("@DataBound only valid on class nodes.", nodes[1])
            return
        }

        def biggestCtor = parent.declaredConstructors.max { it.parameters.length }

        biggestCtor.addAnnotation(new AnnotationNode(new ClassNode(DataBoundConstructor.class)))
    }
}
