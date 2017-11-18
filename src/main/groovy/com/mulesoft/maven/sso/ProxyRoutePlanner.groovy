package com.mulesoft.maven.sso

import org.apache.http.HttpHost
import org.apache.http.impl.conn.DefaultProxyRoutePlanner
import org.apache.maven.wagon.proxy.ProxyInfo

// Maven takes care of deciding whether we need a proxy or not
class ProxyRoutePlanner extends DefaultProxyRoutePlanner {
    ProxyRoutePlanner(ProxyInfo proxyInfo) {
        super(new HttpHost(proxyInfo.host, proxyInfo.port))
    }
}
