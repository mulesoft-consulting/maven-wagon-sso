package com.mulesoft.maven.sso

import io.vertx.core.http.HttpServerRequest
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class WindowsFriendlyWebClientTest implements WebServerHelper {
    @Test
    void preservesCredentialProvider() {
        // arrange
        def mockCredProvider = new CredentialsProvider() {
            @Override
            void setCredentials(AuthScope authScope, Credentials credentials) {

            }

            @Override
            Credentials getCredentials(AuthScope authScope) {
                new UsernamePasswordCredentials('foo', 'bar')
            }

            @Override
            void clear() {

            }
        }
        def client = new WindowsFriendlyWebClient(null,
                                                  mockCredProvider)
        httpServer.requestHandler { HttpServerRequest request ->
            println "fake server got ${request.absoluteURI()}"
            request.headers().each { header ->
                println " header ${header.key} value ${header.value}"
            }
            request.response().with {
                if (!request.getHeader('Authorization')) {
                    statusCode = 401
                    putHeader('WWW-Authenticate', 'Basic realm="SSO Realm"')
                    end()
                    return
                }
                statusCode = 200
                end('foobar')
            }
        }.listen(8081, 'localhost')

        // act
        def response = client.getPage('http://localhost:8081/')

        // assert
        assertThat response.webResponse.contentAsString,
                   is(equalTo('foobar'))
    }
}
