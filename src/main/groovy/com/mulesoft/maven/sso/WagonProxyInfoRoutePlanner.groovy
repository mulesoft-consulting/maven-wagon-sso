package com.mulesoft.maven.sso

import org.apache.http.HttpException
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.impl.conn.DefaultProxyRoutePlanner
import org.apache.http.protocol.HttpContext
import org.apache.maven.wagon.proxy.ProxyInfo
import org.apache.maven.wagon.proxy.ProxyUtils

// the Wagon itself doesn't need this because it handles proxy bypasses in its own
// but our access token fetcher doesn't so it needs it
class WagonProxyInfoRoutePlanner extends DefaultProxyRoutePlanner {
    private final ProxyInfo proxyInfo

    WagonProxyInfoRoutePlanner(ProxyInfo proxyInfo) {
        super(new HttpHost(proxyInfo.host, proxyInfo.port))
        this.proxyInfo = proxyInfo
    }

    @Override
    HttpRoute determineRoute(HttpHost host, HttpRequest request, HttpContext context) throws HttpException {
        def isDirect = isDirect(host.hostName)
        isDirect ? new HttpRoute(host) : super.determineRoute(host, request, context)
    }

    boolean isDirect(String hostname) {
        ProxyUtils.validateNonProxyHosts(this.proxyInfo, hostname)
    }
}
