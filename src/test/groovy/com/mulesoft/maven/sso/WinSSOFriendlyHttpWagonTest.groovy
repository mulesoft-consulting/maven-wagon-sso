package com.mulesoft.maven.sso

import org.junit.BeforeClass
import org.junit.Test

class WinSSOFriendlyHttpWagonTest {
    static String mvnPath

    @BeforeClass
    static void downloadMaven() {
        def mavenUrl = 'http://www-us.apache.org/dist/maven/maven-3/3.5.2/binaries/apache-maven-3.5.2-bin.zip'
        def tmpDir = new File('tmp')
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }
        def mavenDir = new File(tmpDir, 'apache-maven-3.5.2')
        def binDir = new File(mavenDir, 'bin')
        assert binDir.exists()
        mvnPath = new File(binDir, 'mvn').absolutePath
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
        println "Maven unzipped at ${mavenDir}, testing"
        executeMavenPhaseOrGoal('--version')
    }

    static void executeMavenPhaseOrGoal(String... goals) {
        def command = "sh ${mvnPath} ${goals.join(' ')}"
        def result = command.execute()
        result.waitForProcessOutput(System.out, System.err)
        assert result.exitValue() == 0
    }

    @Test
    void doStuff() {
        // arrange

        // act

        // assert
        fail 'write this'
    }
}
