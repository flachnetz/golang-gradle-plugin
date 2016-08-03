package de.flachnetz.golang

import org.gradle.api.*

/**
 *
 */
class GoPluginTest extends GroovyTestCase {
    Project project

    def testDir = new File("build/tmp/test/${getClass().simpleName}")

    def setup() {
        project = ProjectBuilder.builder().withName('ReleasePluginTest').withProjectDir(testDir).build()
        def testVersionPropertyFile = project.file('version.properties')
        testVersionPropertyFile.withWriter { w ->
            w.writeLine 'version=1.2'
        }
        project.apply plugin: ReleasePlugin
        project.release.scmAdapters = [TestAdapter]

        project.createScmAdapter.execute()
    }

    def 'plugin is successfully applied'() {
        expect:
        assert project.tasks.release

    }
}

