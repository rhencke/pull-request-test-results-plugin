/*
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License ("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file.  This particular file is subject to the "Classpath"
 * exception as provided in the GPL Version 2 section of the License file
 * that accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * The Original Software is SezPoz. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Copyright 2006-2011 Oracle
 * Corporation. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package net.java.sezpoz.impl;

import net.java.sezpoz.Indexable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Processor for indexable annotations.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions("sezpoz.quiet")
public class Indexer extends AbstractProcessor {

    public static final String METAINF_ANNOTATIONS = "META-INF/annotations/";

    /**
     * public for ServiceLoader
     */
    public Indexer() {
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            // TODO we should not write until processingOver
            return false;
        }

        Class<?> roundEnvClass = roundEnv.getClass();
        Set<TypeElement> fauxAnnotations = new HashSet<>(annotations);
        for (Element indexable : roundEnv.getElementsAnnotatedWith(Indexable.class)) {
            String error = verifyIndexable(indexable);
            if (error != null) {
                processingEnv.getMessager().printMessage(Kind.ERROR, error, indexable);
            } else {
                Retention retention = indexable.getAnnotation(Retention.class);
                if (retention == null || retention.value() != RetentionPolicy.SOURCE) {
                    processingEnv.getMessager().printMessage(Kind.WARNING, "should be marked @Retention(RetentionPolicy.SOURCE)", indexable);
                }
            }
        }
        if (Objects.equals(roundEnvClass.getName(), "org.jetbrains.kotlin.annotation.RoundEnvironmentWrapper")) {
            // Cheat - Kotlin wraps the RoundEnvironment, and exposes a property that has what we
            // need, since roundEnv.getElementsAnnotatedWith unfortunately does not correctly as implemented in
            // kotlin-annotation-processing currently.
            try {
                Object kap = roundEnvClass.getMethod("getKotlinAnnotationsProvider").invoke(roundEnv);
                //noinspection unchecked
                Map<String, Set<?>> elemsMap = (Map<String, Set<?>>) kap
                        .getClass()
                        .getMethod("getAnnotatedKotlinElements")
                        .invoke(kap);
                for (String classFqName : elemsMap.keySet()) {
                    // More cheating - Kotlin's implementation of these elements gives us a classFqName
                    // they use internally for this same purpose.
                    TypeElement elemType = processingEnv.getElementUtils().getTypeElement(classFqName);
                    if (!fauxAnnotations.contains(elemType) && verifyIndexable(elemType) == null) {
                        fauxAnnotations.add(elemType);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                processingEnv.getMessager().printMessage(Kind.ERROR, e.toString());
                return false;
            }
        }

        // map from indexable annotation names, to actual uses
        Map<String, Map<String, SerAnnotatedElement>> output = new TreeMap<String, Map<String, SerAnnotatedElement>>();
        Map<String, Collection<Element>> originatingElementsByAnn = new HashMap<String, Collection<Element>>();
        scan(fauxAnnotations, originatingElementsByAnn, roundEnv, output);
        write(output, originatingElementsByAnn);
        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private void scan(Set<? extends TypeElement> annotations, Map<String, Collection<Element>> originatingElementsByAnn,
                      RoundEnvironment roundEnv, Map<String, Map<String, SerAnnotatedElement>> output) {
        for (TypeElement ann : annotations) {
            AnnotationMirror indexable = null;
            for (AnnotationMirror _indexable : processingEnv.getElementUtils().getAllAnnotationMirrors(ann)) {
                if (processingEnv.getElementUtils().getBinaryName((TypeElement) _indexable.getAnnotationType().asElement()).
                        contentEquals(Indexable.class.getName())) {
                    indexable = _indexable;
                    break;
                }
            }
            if (indexable == null) {
                continue;
            }
            String annName = processingEnv.getElementUtils().getBinaryName(ann).toString();
            Collection<Element> originatingElements = originatingElementsByAnn.get(annName);
            if (originatingElements == null) {
                originatingElements = new ArrayList<Element>();
                originatingElementsByAnn.put(annName, originatingElements);
            }
            for (Element elt : roundEnv.getElementsAnnotatedWith(ann)) {
                AnnotationMirror marked = null;
                for (AnnotationMirror _marked : elt.getAnnotationMirrors()) {
                    if (processingEnv.getElementUtils().getBinaryName((TypeElement) _marked.getAnnotationType().asElement()).contentEquals(annName)) {
                        marked = _marked;
                        break;
                    }
                }
                if (marked == null) {
                    continue;
                }
                String error = verify(elt, indexable);
                if (error != null) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, error, elt, marked);
                    continue;
                }
                originatingElements.add(elt);
                Map<String, SerAnnotatedElement> existingOutput = output.get(annName);
                if (existingOutput == null) {
                    existingOutput = new TreeMap<String, SerAnnotatedElement>();
                    output.put(annName, existingOutput);
                }
                SerAnnotatedElement ser = makeSerAnnotatedElement(elt, ann);
                if (!Boolean.parseBoolean(processingEnv.getOptions().get("sezpoz.quiet"))) {
                    processingEnv.getMessager().printMessage(Kind.NOTE, ser.className.replace('$', '.') +
                            (ser.memberName != null ? "." + ser.memberName : "") +
                            " indexed under " + annName.replace('$', '.'));
                }
                existingOutput.put(ser.key(), ser);
            }
        }
    }

    private void write(Map<String, Map<String, SerAnnotatedElement>> output, Map<String, Collection<Element>> originatingElementsByAnn) {
        for (Map.Entry<String, Map<String, SerAnnotatedElement>> outputEntry : output.entrySet()) {
            String annName = outputEntry.getKey();
            try {
                Map<String, SerAnnotatedElement> elements = outputEntry.getValue();
                try {
                    FileObject in = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", METAINF_ANNOTATIONS + annName);
                    // Read existing annotations, for incremental compilation.
                    InputStream is = in.openInputStream();
                    try {
                        ObjectInputStream ois = new ObjectInputStream(is);
                        while (true) {
                            SerAnnotatedElement el;
                            try {
                                el = (SerAnnotatedElement) ois.readObject();
                            } catch (ClassNotFoundException cnfe) {
                                throw new IOException(cnfe.toString());
                            }
                            if (el == null) {
                                break;
                            }
                            if (!elements.containsKey(el.key())) {
                                elements.put(el.key(), el);
                            }
                        }
                    } finally {
                        is.close();
                    }
                } catch (FileNotFoundException x) {
                    // OK, created for the first time
                }
                // For reasons I don't understand, this file is successfully created
                // after annotation processing, and then immediately deleted.
                // But, text files aren't deleted.
                // Sooooo... disguise it as a text file, and rename it afterwards, in Gradle.
                // TODO: this is a dumb hack
                FileObject out = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                        "", METAINF_ANNOTATIONS + annName + ".not.txt",
                        originatingElementsByAnn.get(annName).toArray(new Element[0]));
                OutputStream os = out.openOutputStream();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(os);
                    for (SerAnnotatedElement el : elements.values()) {
                        oos.writeObject(el);
                    }
                    oos.writeObject(null);
                    oos.flush();
                } finally {
                    os.close();
                }
                out = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                        "", METAINF_ANNOTATIONS + annName + ".txt",
                        originatingElementsByAnn.get(annName).toArray(new Element[0]));
                Writer w = out.openWriter();
                try {
                    w.write("# informational; use java -jar sezpoz.jar to see authoritative contents\n");
                    for (SerAnnotatedElement el : elements.values()) {
                        w.write(el.toString());
                        w.write('\n');
                    }
                } finally {
                    w.close();
                }
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR, x.toString());
            }
        }
    }

    private SerAnnotatedElement makeSerAnnotatedElement(Element elt, TypeElement ann) {
        String className, memberName;
        boolean isMethod;
        switch (elt.getKind()) {
            case CLASS:
                className = processingEnv.getElementUtils().getBinaryName((TypeElement) elt).toString();
                memberName = null;
                isMethod = false;
                break;
            case METHOD:
                className = processingEnv.getElementUtils().getBinaryName((TypeElement) elt.getEnclosingElement()).toString();
                memberName = elt.getSimpleName().toString();
                isMethod = true;
                break;
            case FIELD:
                className = processingEnv.getElementUtils().getBinaryName((TypeElement) elt.getEnclosingElement()).toString();
                memberName = elt.getSimpleName().toString();
                isMethod = false;
                break;
            default:
                throw new AssertionError(elt.getKind());
        }
        return new SerAnnotatedElement(className, memberName, isMethod, translate(elt.getAnnotationMirrors(), ann));
    }

    private TreeMap<String, Object> translate(List<? extends AnnotationMirror> mirrors, TypeElement ann) {
        TreeMap<String, Object> values = new TreeMap<String, Object>();
        for (AnnotationMirror mirror : mirrors) {
            if (processingEnv.getTypeUtils().isSameType(mirror.getAnnotationType(), ann.asType())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                    values.put(entry.getKey().getSimpleName().toString(), translate(entry.getValue().getValue()));
                }
            }
        }
        return values;
    }

    private Object translate(Object annval) {
        if (annval instanceof List) {
            @SuppressWarnings("unchecked")
            List<? extends AnnotationValue> annvals = (List) annval;
            List<Object> values = new ArrayList<Object>(annvals.size());
            for (AnnotationValue v : annvals) {
                values.add(translate(v.getValue()));
            }
            return values;
        } else if (annval instanceof TypeMirror) {
            return new SerTypeConst(processingEnv.getElementUtils().getBinaryName(
                    (TypeElement) processingEnv.getTypeUtils().asElement((TypeMirror) annval)).toString());
        } else if (annval instanceof VariableElement) {
            VariableElement elt = (VariableElement) annval;
            return new SerEnumConst(processingEnv.getElementUtils().getBinaryName(
                    (TypeElement) elt.getEnclosingElement()).toString(), elt.getSimpleName().toString());
        } else if (annval instanceof AnnotationMirror) {
            AnnotationMirror am = (AnnotationMirror) annval;
            TreeMap<String, Object> values = new TreeMap<String, Object>();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
                values.put(entry.getKey().getSimpleName().toString(), translate(entry.getValue().getValue()));
            }
            return new SerAnnConst(am.getAnnotationType().toString(), values);// XXX or use asElement?
        } else {
            return annval;
        }
    }

    /**
     * Checks metadata of a proposed registration.
     *
     * @param registration a class, method, or field
     * @param annotation   an indexable annotation applied to {@code registration}
     * @param indexable    {@link Indexable} annotation on that annotation
     * @return an error message, or null if it is valid
     */
    private String verify(Element registration, AnnotationMirror indexable) {
        if (!registration.getModifiers().contains(Modifier.PUBLIC)) {
            return "annotated elements must be public";
        }
        TypeMirror supertype = processingEnv.getElementUtils().getTypeElement("java.lang.Object").asType();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : indexable.getElementValues().entrySet()) {
            if (!entry.getKey().getSimpleName().contentEquals("type")) {
                continue;
            }
            supertype = (TypeMirror) entry.getValue().getValue();
        }
        TypeMirror thistype;
        switch (registration.getKind()) {
            case CLASS:
                if (registration.getModifiers().contains(Modifier.ABSTRACT)) {
                    return "annotated classes must not be abstract";
                }
                boolean hasDefaultCtor = false;
                for (ExecutableElement constructor : ElementFilter.constructorsIn(registration.getEnclosedElements())) {
                    if (constructor.getModifiers().contains(Modifier.PUBLIC) && constructor.getParameters().isEmpty()) {
                        hasDefaultCtor = true;
                        break;
                    }
                }
                if (!hasDefaultCtor) {
                    return "annotated classes must have a public no-argument constructor";
                }
                Element enclosing = registration.getEnclosingElement();
                if (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE && !registration.getModifiers().contains(Modifier.STATIC)) {
                    return "annotated nested classes must be static";
                }
                thistype = registration.asType();
                break;
            case METHOD:
                if (!registration.getEnclosingElement().getModifiers().contains(Modifier.PUBLIC)) {
                    return "annotated methods must be contained in a public class";
                }
                if (!registration.getModifiers().contains(Modifier.STATIC)) {
                    return "annotated methods must be static";
                }
                if (!((ExecutableElement) registration).getParameters().isEmpty()) {
                    return "annotated methods must take no parameters";
                }
                thistype = ((ExecutableElement) registration).getReturnType();
                break;
            case FIELD:
                if (!registration.getEnclosingElement().getModifiers().contains(Modifier.PUBLIC)) {
                    return "annotated fields must be contained in a public class";
                }
                if (!registration.getModifiers().contains(Modifier.STATIC)) {
                    return "annotated fields must be static";
                }
                if (!registration.getModifiers().contains(Modifier.FINAL)) {
                    return "annotated fields must be final";
                }
                thistype = ((VariableElement) registration).asType();
                break;
            default:
                return "annotations must be on classes, methods, or fields";
        }
        if (!processingEnv.getTypeUtils().isAssignable(thistype, supertype)) {
            return "not assignable to " + supertype;
        }
        return null;
    }

    private String verifyIndexable(Element indexable) {
        if (indexable.getAnnotation(Inherited.class) != null) {
            return "cannot be @Inherited";
        }
        Target target = indexable.getAnnotation(Target.class);
        if (target == null) {
            return "must be marked with @Target";
        }
        if (target.value().length == 0) {
            return "must have at least one element type in @Target";
        }
        for (ElementType type : target.value()) {
            switch (type) {
                case TYPE:
                case METHOD:
                case FIELD:
                    continue;
                default:
                    return "should not be permitted on element type " + type;
            }
        }
        return null;
    }

}
