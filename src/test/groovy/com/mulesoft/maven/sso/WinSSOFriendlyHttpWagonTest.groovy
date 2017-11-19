package com.mulesoft.maven.sso

import groovy.json.JsonOutput
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerRequest
import org.apache.tools.ant.taskdefs.condition.Os
import org.codehaus.plexus.util.FileUtils
import org.junit.*

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class WinSSOFriendlyHttpWagonTest implements FileHelper {
    static String mvnExecutablePath
    static File mavenDir
    List<HttpServer> startedServers
    static File tmpDir = new File('tmp')
    static File scratchPad = new File(tmpDir, 'scratchpad')

    @BeforeClass
    static void setup() {
        downloadMaven()
        copyJar()
    }

    @Before
    void cleanup() {
        this.startedServers = []
        if (System.getProperty('SKIP_REPO_CLEAN')) {
            println '***********Skipping repo clean!'
            return
        }
        def repoDir = new File(tmpDir, 'repoDir')
        if (repoDir.exists()) {
            assert repoDir.deleteDir()
        }
        if (scratchPad.exists()) {
            assert scratchPad.deleteDir()
        }
    }

    @After
    void shutdownServers() {
        this.startedServers.each { server ->
            println "Closing server ${server}..."
            server.close()
        }
    }

    HttpServer getHttpServer() {
        def httpServer = Vertx.vertx().createHttpServer()
        startedServers << httpServer
        httpServer
    }

    static void downloadMaven() {
        def mavenUrl = 'http://www-us.apache.org/dist/maven/maven-3/3.5.2/binaries/apache-maven-3.5.2-bin.zip'
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

    void runMaven(String settingsFilename,
                  String pomFileName = 'pom_1_repo.xml',
                  String... goals = ['clean']) {
        def settings = getFile(testResources, settingsFilename)
        assert settings.exists()
        def project = getFile(testResources, pomFileName)
        assert project.exists()
        executeMavenPhaseOrGoal(
                "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -e -B",
                // quiet things down
                "-s ${settings.absolutePath}",
                "-f ${project.absolutePath}",
                *goals)
    }

    @Test
    void simpleFetch() {
        // arrange
        List<String> requestedUrls = []
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            requestedUrls << uri
            request.response().with {
                // we're more interested in the request than what Maven does with the response
                statusCode = 404
                end()
            }
        }.listen(8081, 'localhost')

        // act
        runMaven 'simple_settings.xml'

        // assert
        assertThat 'Expected URLs to run through our repo!',
                   requestedUrls.any(),
                   is(equalTo(true))
    }

    @Test
    void simpleFetch_spnego() {
        // arrange
        // Could spin up Kerberos/Docker/etc. but Apache's code is what's handling SPNego
        // so we don't need to test that as thoroughly
        Assume.assumeTrue('Easier to run test on Windows',
                          Os.isFamily(Os.FAMILY_WINDOWS))
        def challengedOnce = false
        String spNegoToken = null
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            println "Fake server got URL ${uri}"
            println "Headers:"
            request.headers().each { kvp ->
                println "Key ${kvp.key} value ${kvp.value}"
            }
            println "End headers"
            request.response().with {
                if (!spNegoToken && !request.getHeader('Authorization')) {
                    challengedOnce = true
                    println "Triggering SPNEGO challenge/401 for ${uri}"
                    statusCode = 401
                    putHeader('WWW-Authenticate', 'Negotiate')
                    end()
                    return
                }
                if (!spNegoToken) {
                    spNegoToken = request.getHeader('Authorization')
                }
                if (uri.endsWith('maven-metadata.xml')) {
                    statusCode = 200
                    def file = new File(testResources, 'simple_pom_artifact_metadata.xml')
                    assert file.exists()
                    end(file.text)
                    return
                }
                if (uri.endsWith('sha1')) {
                    statusCode = 200
                    end('dbd5c806a03197aff7179d49f2b7db586887e8a7')
                    return
                }
                if (uri.contains('test.artifact')) {
                    if (uri.endsWith('sha1')) {
                        statusCode = 200
                        end('1c60353fb663ddec3c69fe6436146a5172ad1b0f')
                        return
                    }
                    println "returning first pass of POM"
                    statusCode = 200
                    putHeader('Content-Type', 'application/xml')
                    def responseText = new File(testResources, 'simple_pom_artifact.xml')
                    assert responseText.exists()
                    end(responseText.text)
                    return
                }
                // we're more interested in the request than what Maven does with the response
                statusCode = 404
                end()
            }
        }.listen(8081, 'localhost')

        // act
        runMaven 'simple_settings.xml',
                 'pom_madeupdependency.xml',
                 'clean',
                 'compile'

        // assert
        assert spNegoToken
    }

    @Test
    void samlFetch_FirstTime() {
        // arrange
        List<String> requestedUrls = []
        def alreadyFetched = false
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            println "Fake server got URL ${uri}"
            println "Headers:"
            request.headers().each { kvp ->
                println " Key ${kvp.key} value ${kvp.value}"
            }
            println "End headers"
            requestedUrls << uri
            request.response().with {
                switch (uri) {
                    case 'http://a_place_that_posts_saml_token/':
                        if (alreadyFetched) {
                            statusCode = 500
                            end('already fetched a token!')
                            return
                        }
                        alreadyFetched = true
                        statusCode = 200
                        putHeader('Content-Type', 'text/html')
                        def file = getFile(testResources, 'auto_post.html')
                        end(file.text)
                        return
                    case 'http://anypoint.test.com/':
                        statusCode = 200
                        putHeader('Content-Type', 'text/html')
                        putHeader('Set-Cookie', 'somestuff=somevalue')
                        end('<foo/>')
                        return
                    case 'http://anypoint.test.com/profile_location/':
                        if (request.getHeader('Cookie') != 'somestuff=somevalue') {
                            println ' no cookie supplied, sending back 401'
                            statusCode = 401
                            end()
                            return
                        }
                        statusCode = 200
                        putHeader('Content-Type', 'application/json')
                        def response = [
                                access_token: 'abcdef',
                                username: 'the_user'
                        ]
                        end(JsonOutput.toJson(response))
                        return
                }
                if (uri.contains('maven-metadata.xml')) {
                    // Anypoint servers do not lock this down
                    statusCode = 200
                    if (uri.endsWith('sha1')) {
                        end('dbd5c806a03197aff7179d49f2b7db586887e8a7')
                        return
                    }
                    println 'Returning first pass of metadata'
                    def file = new File(testResources, 'simple_pom_artifact_metadata.xml')
                    assert file.exists()
                    end(file.text)
                    return
                }
                if (uri.contains('test.artifact')) {
                    // new UsernamePasswordCredentials('~~~Token~~~', 'abcdef')
                    if (request.getHeader('Authorization') != 'Basic fn5+VG9rZW5+fn46YWJjZGVm') {
                        println ' Unauthorized, returning a 401'
                        statusCode = 401
                        putHeader('WWW-Authenticate', 'Basic realm="User Visible Realm"')
                        end()
                        return
                    }
                    if (uri.endsWith('sha1')) {
                        statusCode = 200
                        end('1c60353fb663ddec3c69fe6436146a5172ad1b0f')
                        return
                    }
                    println "returning first pass of POM"
                    statusCode = 200
                    putHeader('Content-Type', 'application/xml')
                    def responseText = new File(testResources, 'simple_pom_artifact.xml')
                    assert responseText.exists()
                    end(responseText.text)
                    return
                }
                // we're more interested in the request than what Maven does with the response
                statusCode = 404
                end()
            }
        }.listen(8081, 'localhost')

        // act
        runMaven 'saml_settings.xml',
                 'pom_madeupdependency.xml',
                 'clean',
                 'compile'

        // assert
        // should have no errors
    }

    @Test
    void samlFetch_expires() {
        // arrange
        List<String> requestedUrls = []
        def expireCounter = 0
        Exception exception = null
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            def isArtifact2 = uri.contains('test.artifact2')
            println "Fake server got URL ${uri}"
            requestedUrls << uri
            request.response().with {
                switch (uri) {
                    case 'http://a_place_that_posts_saml_token/':
                        statusCode = 200
                        putHeader('Content-Type', 'text/html')
                        def file = getFile(testResources, 'auto_post.html')
                        end(file.text)
                        return
                    case 'http://anypoint.test.com/':
                        statusCode = 200
                        putHeader('Content-Type', 'text/html')
                        putHeader('Set-Cookie', 'somestuff=somevalue')
                        end('<foo/>')
                        return
                    case 'http://anypoint.test.com/profile_location/':
                        if (request.getHeader('Cookie') != 'somestuff=somevalue') {
                            println ' no cookie supplied, sending back 401'
                            statusCode = 401
                            end()
                            return
                        }
                        statusCode = 200
                        putHeader('Content-Type', 'application/json')
                        def response = [
                                access_token: expireCounter >= 2 ? 'foobar' : 'abcdef',
                                username: 'the_user'
                        ]
                        end(JsonOutput.toJson(response))
                        return
                }
                if (uri.contains('maven-metadata.xml')) {
                    // Anypoint servers do not lock this down
                    statusCode = 200
                    if (uri.endsWith('sha1')) {
                        end('dbd5c806a03197aff7179d49f2b7db586887e8a7')
                        return
                    }
                    println 'Returning metadata'
                    def file = new File(testResources, 'simple_pom_artifact_metadata.xml')
                    assert file.exists()
                    end(file.text)
                    return
                }
                if (uri.contains('test.artifact')) {
                    println "*Headers:"
                    request.headers().each { kvp ->
                        println "  Key ${kvp.key} value ${kvp.value}"
                    }
                    println "*End headers"
                    def expectedPassword = expireCounter >= 2 ? 'foobar' : 'abcdef'
                    if (expireCounter >= 2 && !request.getHeader('Authorization')) {
                        exception = new Exception('We stopped supplying auth altogether. Wagon scope issue?')
                        exception.printStackTrace()
                    }
                    def expectedString = Base64.encoder.encodeToString("~~~Token~~~:${expectedPassword}".bytes)
                    println "Expecting auth string '${expectedString}'"
                    def authMatches = request.getHeader('Authorization') == "Basic ${expectedString}"
                    if (!authMatches) {
                        println ' Unauthorized, returning a 401'
                        statusCode = 401
                        putHeader('WWW-Authenticate', 'Basic realm="User Visible Realm"')
                        end()
                        return
                    }
                    println "Authentication passed! Increasing expire counter to ${expireCounter + 1} from ${expireCounter}..."
                    expireCounter++
                    if (uri.endsWith('sha1')) {
                        statusCode = 200
                        end('1c60353fb663ddec3c69fe6436146a5172ad1b0f')
                        return
                    }
                    def pomFilename = isArtifact2 ? 'simple_pom_artifact2.xml' : 'simple_pom_artifact.xml'
                    println "returning first pass of POM ${pomFilename}"
                    statusCode = 200
                    putHeader('Content-Type', 'application/xml')
                    def responseText = new File(testResources, pomFilename)
                    assert responseText.exists()
                    end(responseText.text)
                    return
                }
                // we're more interested in the request than what Maven does with the response
                statusCode = 404
                end()
            }
        }.listen(8081, 'localhost')

        // act
        runMaven 'saml_settings.xml',
                 'pom_2madeupdependencies.xml',
                 'clean',
                 'compile',
                '-Danypoint.token.timeout.ms=5'

        // assert
        assert !exception
    }
}
