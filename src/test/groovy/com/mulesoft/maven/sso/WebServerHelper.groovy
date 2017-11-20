package com.mulesoft.maven.sso

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import org.junit.After
import org.junit.Before

trait WebServerHelper {
    List<HttpServer> startedServers

    @Before
    void initStartServers() {
        this.startedServers = []
    }

    @After
    void shutdownWebServers() {
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
}