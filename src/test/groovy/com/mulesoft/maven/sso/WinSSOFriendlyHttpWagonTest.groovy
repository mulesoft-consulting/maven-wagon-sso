package com.mulesoft.maven.sso

import org.codehaus.plexus.util.FileUtils
import org.junit.BeforeClass
import org.junit.Test

class WinSSOFriendlyHttpWagonTest {
    static String mvnExecutablePath
    static File mavenDir

    @BeforeClass
    static void setup() {
        downloadMaven()
        copyJar()
    }

    static void downloadMaven() {
        def mavenUrl = 'http://www-us.apache.org/dist/maven/maven-3/3.5.2/binaries/apache-maven-3.5.2-bin.zip'
        def tmpDir = new File('tmp')
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }
        mavenDir = new File(tmpDir, 'apache-maven-3.5.2')
        def binDir = new File(mavenDir, 'bin')
        mvnExecutablePath = new File(binDir, 'mvn').absolutePath
        if (mavenDir.exists()) {
            executeMavenPhaseOrGoal('--version')
            println 'Maven already ready to go!'
            return
        }
        println "Downloading test copy of Maven from ${mavenUrl}..."
        def zipFile = new File(tmpDir, 'maven-bin.zip')
        def zipFileStream = zipFile.newOutputStream()
        zipFileStream << new URL(mavenUrl).openStream()
        zipFileStream.close()
        def antBuilder = new AntBuilder()
        antBuilder.unzip(src: zipFile.absolutePath,
                         dest: tmpDir)
        assert zipFile.delete()
        assert binDir.exists()
        println "Maven unzipped at ${mavenDir}, testing"
        executeMavenPhaseOrGoal('--version')
    }

    static void executeMavenPhaseOrGoal(String... goals) {
        def command = "sh ${mvnExecutablePath} ${goals.join(' ')}"
        println "Running Maven command: '${command}'"
        def result = command.execute()
        result.waitForProcessOutput(System.out, System.err)
        assert result.exitValue() == 0
    }

    static void copyJar() {
        def libDir = new File(mavenDir, 'lib')
        assert libDir.exists()
        def extDir = new File(libDir, 'ext')
        assert extDir.exists()
        println 'Building JAR via Gradle...'
        def result = 'sh gradlew jar'.execute()
        result.waitForProcessOutput(System.out, System.err)
        assert result.exitValue() == 0
        def jarFiles = new FileNameFinder().getFileNames('build/libs', '**/*.jar')
        assert jarFiles.size() == 1
        def jarFile = new File(jarFiles[0])
        assert jarFile.exists()
        println "Copying built JAR ${jarFile} into ${extDir}"
        FileUtils.copyFileToDirectory(jarFile, extDir)
    }

    static File getFile(String... parts) {
        parts.inject { existing, part ->
            def parent = existing instanceof String ? new File(existing) : existing
            new File(parent, part)
        }
    }

    @Test
    void simpleFetch() {
        // arrange
        def settings = getFile('src', 'test', 'resources', 'simple_settings.xml')
        assert settings.exists()
        def project = getFile('src', 'test', 'resources', 'theProjectPom.xml')
        assert project.exists()

        // act
        executeMavenPhaseOrGoal("-s ${settings.absolutePath}",
                                "-f ${project.absolutePath}",
                                '-U', // forces a repo fetch
                                'clean',
                                'compile')

        // assert
        fail 'write this'
    }
}
