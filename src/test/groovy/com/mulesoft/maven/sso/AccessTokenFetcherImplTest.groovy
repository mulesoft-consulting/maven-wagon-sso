package com.mulesoft.maven.sso

import groovy.json.JsonOutput
import io.vertx.core.http.HttpServerRequest
import org.apache.maven.wagon.proxy.ProxyInfo
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class AccessTokenFetcherImplTest implements FileHelper, WebServerHelper {
    @Test
    void getAccessToken_noProxy() {
        // arrange
        def fetcher = new AccessTokenFetcherImpl(null,
                                                 'http://localhost:8081/profile',
                                                 'http://localhost:8081/idpurl')
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            println "fake server got ${uri}"
            request.headers().each { header ->
                println " header ${header.key} value ${header.value}"
            }
            request.response().with {
                switch (uri) {
                    case 'http://localhost:8081/idpurl':
                        statusCode = 200
                        putHeader('Content-Type', 'text/html')
                        putHeader('Set-Cookie', 'somestuff=somevalue')
                        end('<foo/>')
                        return
                    case 'http://localhost:8081/profile':
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
                                username    : 'the_user'
                        ]
                        end(JsonOutput.toJson(response))
                        return
                    default:
                        statusCode = 404
                        end()
                }
            }
        }.listen(8081, 'localhost')

        // act
        def result = fetcher.getAccessToken()

        // assert
        assertThat result,
                   is(equalTo('abcdef'))
    }

    @Test
    void getAccessToken() {
        // arrange
        def proxyInfo = new ProxyInfo()
        proxyInfo.host = 'localhost'
        proxyInfo.port = 8081
        def fetcher = new AccessTokenFetcherImpl(proxyInfo,
                                                 'http://anypoint.test.com/profile_location/',
                                                 'http://a_place_that_posts_saml_token')
        httpServer.requestHandler { HttpServerRequest request ->
            def uri = request.absoluteURI()
            println "fake proxy got ${uri}"
            request.headers().each { header ->
                println " header ${header.key} value ${header.value}"
            }
            request.response().with {
                if (uri.endsWith('css')) {
                    statusCode = 200
                    end('foobar')
                    return
                }
                switch (uri) {
                    case 'http://a_place_that_posts_saml_token/':
                        statusCode = 302
                        putHeader('Location', 'http://redirect.site/')
                        end()
                        return
                    case 'http://redirect.site/':
                        statusCode = 200
                        putHeader('Content-Type', 'text/html')
                        def file = getFile(testResources, 'auto_post.html')
                        end(file.text)
                        return
                    case 'http://anypoint.test.com/':
                        def form = request.formAttributes()
                        println "got form attr ${form}"
                        if (form['SAMLResponse'] != 'the SAML stuff' || form['RelayState'] != 'the relay state2') {
                            statusCode = 400
                            end('no saml posted')
                            return
                        }
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
                                username    : 'the_user'
                        ]
                        end(JsonOutput.toJson(response))
                        return
                    default:
                        statusCode = 404
                        end()
                }
            }
        }.listen(8081, 'localhost')

        // act
        def result = fetcher.getAccessToken()

        // assert
        assertThat result,
                   is(equalTo('abcdef'))
    }
}
