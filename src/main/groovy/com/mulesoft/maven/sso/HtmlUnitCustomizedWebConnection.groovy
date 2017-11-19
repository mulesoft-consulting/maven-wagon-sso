package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.HttpWebConnection
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.httpclient.HtmlUnitRedirectStrategie
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.WinHttpClients
import org.apache.maven.wagon.proxy.ProxyInfo

class HtmlUnitCustomizedWebConnection extends HttpWebConnection {
    private final ProxyInfo proxyInfo

    HtmlUnitCustomizedWebConnection(WebClient webClient,
                                    ProxyInfo proxyInfo) {
        super(webClient)
        this.proxyInfo = proxyInfo
    }

    @Override
    protected HttpClientBuilder createHttpClient() {
        // handles SPNego, etc. for us
        def builder = WinHttpClients.custom()
        builder.routePlanner = new WagonProxyInfoRoutePlanner(proxyInfo)
        builder.setRedirectStrategy(new HtmlUnitRedirectStrategie())
        builder.setMaxConnPerRoute(6)
        return builder
    }
}
