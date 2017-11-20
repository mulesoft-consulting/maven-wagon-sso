package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.HttpWebConnection
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.httpclient.HtmlUnitRedirectStrategie
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.WinHttpClients
import org.apache.maven.wagon.proxy.ProxyInfo

class WindowsFriendlyWebConnection extends HttpWebConnection {
    private final ProxyInfo proxyInfo

    WindowsFriendlyWebConnection(WebClient webClient,
                                 ProxyInfo proxyInfo) {
        super(webClient)
        this.proxyInfo = proxyInfo
        // htmlunit will overwrite what our builder below does when it runs
        // unfortunately we can't fetch the credentials provider from the builder itself
    }

    @Override
    protected HttpClientBuilder createHttpClient() {
        // handles SPNego, etc. for us
        def builder = WinHttpClients.custom()
        // proxies are optional
        if (proxyInfo) {
            builder.routePlanner = new WagonProxyInfoRoutePlanner(proxyInfo)
        }
        builder.setRedirectStrategy(new HtmlUnitRedirectStrategie())
        builder.setMaxConnPerRoute(6)
        return builder
    }
}
