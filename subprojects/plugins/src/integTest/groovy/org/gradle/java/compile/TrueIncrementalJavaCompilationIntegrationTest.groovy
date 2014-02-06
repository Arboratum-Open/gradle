package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * by Szczepan Faber, created at: 1/15/14
 */
class TrueIncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'java'
                //compileJava.options.fork = true
            }

            compileJava {
                def times = [:]
                doFirst {
                    fileTree("build/classes/main").each {
                        if (it.file) {
                            times[it] = it.lastModified()
                        }
                    }
                }
                doLast {
                    sleep(1100)
                    def changedFiles = ""
                    def unchangedFiles = ""
                    times.each { k,v ->
                        if (k.lastModified() != v) {
                            changedFiles += k.name + ","
                        } else {
                            unchangedFiles += k.name + ","
                        }
                    }
                    file("changedFiles.txt").text = changedFiles
                    file("unchangedFiles.txt").text = unchangedFiles
                }
            }
        """

        file("src/main/java/org/Person.java") << """package org;
        public interface Person {
            String getName();
        }"""
        file("src/main/java/org/PersonImpl.java") << """package org;
        public class PersonImpl implements Person {
            public String getName() { return "Szczepan"; }
        }"""
        file("src/main/java/org/AnotherPersonImpl.java") << """package org;
        public class AnotherPersonImpl extends PersonImpl {
            public String getName() { return "Szczepan Faber " + WithConst.X; }
        }"""
        file("src/main/java/org/WithConst.java") << """package org;
        public class WithConst {
            final static int X = 100;
        }"""
    }

    Set getChangedFiles() {
        file("changedFiles.txt").text.split(",").findAll { it.length() > 0 }.collect { it.replaceAll("\\.class", "")}
    }

    Set getUnchangedFiles() {
        file("unchangedFiles.txt").text.split(",").findAll { it.length() > 0 }.collect { it.replaceAll("\\.class", "")}
    }

    def "only subset of output classes changes"() {
        when: run "compileJava"

        then:
        changedFiles.empty
        unchangedFiles.empty

        when:
        file("src/main/java/org/Person.java").text = """package org;
        public interface Person {
            String name();
        }"""
        file("src/main/java/org/PersonImpl.java").text = """package org;
        public class PersonImpl implements Person {
            public String name() { return "Szczepan"; }
        }"""

        run "compileJava"

        then:
        changedFiles == ['AnotherPersonImpl', 'PersonImpl', 'Person'] as Set
    }

    def "touches only the output class that was changed"() {
        run "compileJava"

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl implements Person {
            public String getName() { return "Hans"; }
        }"""

        when: run "compileJava"

        then: changedFiles == ['AnotherPersonImpl'] as Set
    }

    def "is sensitive to class deletion"() {
        run "compileJava"

        assert file("src/main/java/org/PersonImpl.java").delete()

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl implements Person {
            public String getName() { return "Hans"; }
        }"""

        when: run "compileJava"

        then:
        !file("build/classes/main/org/PersonImpl.class").exists()
        changedFiles == ['AnotherPersonImpl', 'PersonImpl'] as Set
    }

    def "is sensitive to inlined constants"() {
        run "compileJava"

        file("src/main/java/org/WithConst.java").text = """package org;
        public class WithConst {
            static final int X = 20;
        }"""

        when: run "compileJava"

        then:
        unchangedFiles.empty
        changedFiles.containsAll(['WithConst', 'AnotherPersonImpl', 'PersonImpl', 'Person'])
    }

    def "is sensitive to source annotations"() {
        file("src/main/java/org/ClassAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) public @interface ClassAnnotation {}
        """
        file("src/main/java/org/SourceAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.SOURCE) public @interface SourceAnnotation {}
        """
        file("src/main/java/org/UsesClassAnnotation.java").text = """package org;
            @ClassAnnotation public class UsesClassAnnotation {}
        """
        file("src/main/java/org/UsesSourceAnnotation.java").text = """package org;
            @SourceAnnotation public class UsesSourceAnnotation {}
        """
        run "compileJava"

        file("src/main/java/org/ClassAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) public @interface ClassAnnotation {
                String foo() default "foo";
            }"""

        when: run "compileJava"

        then:
        unchangedFiles.empty
        changedFiles.containsAll(['WithConst', 'AnotherPersonImpl', 'PersonImpl', 'Person'])
    }

    def "understands inter-project dependencies"() {
        settingsFile << "include 'api'"
        buildFile << "dependencies { compile project(':api') }"

        file("api/src/main/java/org/A.java") << """package org; public class A {}"""
        file("api/src/main/java/org/B.java") << """package org; public class B {}"""

        file("src/main/java/org/ConsumesA.java") << """package org;
            public class ConsumesA { A a = new A(); }
        """
        file("src/main/java/org/ConsumesB.java") << """package org;
            public class ConsumesB { B b = new B(); }
        """

        run "compileJava"

        file("api/src/main/java/org/B.java").text = """package org; public class B {
            public B() { System.out.println("foo"); }
        }
        """

        when: run "compileJava"

        then:
        changedFiles == ['ConsumesB'] as Set
        unchangedFiles.contains('ConsumesA')
    }
}
