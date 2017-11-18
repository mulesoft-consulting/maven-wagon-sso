package com.mulesoft.maven.sso

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerRequest
import org.apache.tools.ant.taskdefs.condition.Os
import org.codehaus.plexus.util.FileUtils
import org.junit.*

import java.util.zip.ZipFile

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class WinSSOFriendlyHttpWagonTest {
    static String mvnExecutablePath
    static File mavenDir
    List<HttpServer> startedServers
    static File tmpDir = new File('tmp')
    static File scratchPad = new File(tmpDir, 'scratchpad')
    static File testResources = getFile('src', 'test', 'resources')

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

    static File getFile(String... parts) {
        getFile(new File(parts[0]),
                *parts[1..-1].toArray())
    }

    static File getFile(File file,
                        String... parts) {
        parts.inject(file) { File existing, String part ->
            new File(existing, part)
        }
    }

    static void runMaven(String settingsFilename,
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
    @Ignore('write these')
    void simpleFetch_too_many_429() {
        // arrange
        // TODO: Can apache handle this itself??

        // act

        // assert
        fail 'write this'
    }

    @Test
    void simpleFetch_not_modified_304() {
        // arrange
        List<String> requestedUrls = []
        def firstCall = false

        def lastModifiedDate = 'Wed, 21 Oct 2015 07:28:00 GMT'
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            println "Fake server got URL ${uri}"
            println "Headers:"
            request.headers().each { kvp ->
                println "Key ${kvp.key} value ${kvp.value}"
            }
            println "End headers"
            requestedUrls << uri
            request.response().with {
                if (uri.contains('maven-metadata.xml')) {
                    statusCode = 200
                    if (uri.endsWith('sha1')) {
                        end('dbd5c806a03197aff7179d49f2b7db586887e8a7')
                        return
                    }
                    if (firstCall) {
                        println "returning 304,"
                        statusCode = 304
                        putHeader('Last-Modified', lastModifiedDate)
                        end()
                        return
                    }
                    println 'Returning first pass of metadata'
                    firstCall = true
                    def file = new File(testResources, 'simple_pom_artifact_metadata.xml')
                    assert file.exists()
                    end(file.text)
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
                    putHeader('Last-Modified', lastModifiedDate)
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
        // get the artifact locally
        runMaven 'simple_settings.xml',
                 'pom_madeupdependency.xml',
                 'clean',
                 'compile'

        // act
        println '-------------------------------2nd Maven run----------------------------------------'
        // should not cause issues when we run again
        runMaven 'simple_settings.xml',
                 'pom_madeupdependency.xml',
                 'clean',
                 'compile',
                 '-U'

        // assert
    }

    @Test
    void proxyFetch() {
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
        List<String> proxyUrls = []
        List<String> proxyHostHeaders = []
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            proxyUrls << uri
            proxyHostHeaders << request.getHeader('Host')
            request.response().with {
                statusCode = 404
                end()
            }
        }.listen(8082, 'localhost')

        // act
        // we won't try and mimic the actual POM, we're just interested in the request
        shouldFail {
            runMaven 'proxy_settings.xml'
        }

        // assert
        assertThat "Expected NO URLs to run through our repo! ${requestedUrls}",
                   requestedUrls.any(),
                   is(equalTo(false))
        assertThat "Expected ${proxyUrls} to have clean plugin!",
                   proxyUrls.contains(
                           'http://localhost:8081/repo/org/apache/maven/plugins/maven-clean-plugin/2.5/maven-clean-plugin-2.5.pom'),
                   is(equalTo(true))
        assertThat proxyHostHeaders.unique(),
                   is(equalTo(['localhost:8081', 'repo.maven.apache.org']))
    }

    @Test
    void proxyFetch_Bypass() {
        // arrange
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            request.response().with {
                // we're more interested in the request than what Maven does with the response
                statusCode = 404
                end()
            }
        }.listen(8081, 'localhost')
        List<String> proxyUrls = []
        List<String> proxyHostHeaders = []
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            proxyUrls << uri
            proxyHostHeaders << request.getHeader('Host')
            request.response().with {
                statusCode = 404
                end()
            }
        }.listen(8082, 'localhost')

        // act
        // we won't fail this time because we exclude Maven central from being proxied
        // but we do still proxy our localhost repo
        runMaven 'proxy_settings_bypass.xml'

        // assert
        assertThat proxyHostHeaders.unique(),
                   is(equalTo(['localhost:8081']))
    }

    @Test
    void deploy() {
        // arrange
        List<String> postedUrls = []
        byte[] jarBytes = null
        byte[] pomBytes = null
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            request.response().with {
                if (request.method().name() == 'GET' && uri.contains('maven-metadata.xml')) {
                    statusCode = 404
                    end()
                    return
                }
                println "Got POST ${uri}..."
                postedUrls << uri
                request.bodyHandler { Buffer buffer ->
                    if (uri.endsWith('pom')) {
                        pomBytes = buffer.bytes
                    } else if (uri.endsWith('.jar')) {
                        jarBytes = buffer.bytes
                    }
                }
                statusCode = 201
                end()
            }
        }.listen(8081, 'localhost')

        // act
        runMaven 'simple_settings.xml',
                 'simple_upload_artifact.xml',
                 'clean',
                 'deploy'

        // assert
        assert jarBytes
        assert pomBytes
        def pomText = new String(pomBytes)
        assertThat pomText,
                   is(containsString('<artifactId>test.artifact</artifactId>'))
        scratchPad.mkdirs()
        def jarFile = new File(scratchPad, 'something.jar')
        jarFile.bytes = jarBytes
        def zipFile = new ZipFile(jarFile)
        def size = 0
        zipFile.entries().each { entry ->
            size++
            println "entry ${entry.name}"
        }
        assertThat size,
                   is(equalTo(7))
    }

    @Test
    @Ignore('write these')
    void deploy_too_many_429() {
        // arrange
        // TODO: Can apache handle this itself??

        // act

        // assert
        fail 'write this'
    }

    @Test
    @Ignore('write these')
    void deploy_redirect_on_post() {
        // arrange
        // TODO: Can apache handle this itself??

        // act

        // assert
        fail 'write this'
    }

    @Test
    @Ignore('write these')
    void resourceExists() {
        // arrange

        // act

        // assert
        fail 'write this'
    }
}
