package com.mulesoft.maven.sso

import org.apache.http.HttpHost
import org.apache.http.impl.conn.DefaultProxyRoutePlanner
import org.apache.maven.wagon.proxy.ProxyInfo

class HtmlUnitProxyRoutePlanner extends DefaultProxyRoutePlanner {
    HtmlUnitProxyRoutePlanner(ProxyInfo proxyInfo) {
        super(new HttpHost(proxyInfo.host, proxyInfo.port))
    }
}
