package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.WebClient
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.auth.win.WindowsCredentialsProvider
import org.apache.http.impl.client.SystemDefaultCredentialsProvider
import org.apache.maven.wagon.proxy.ProxyInfo

class WindowsFriendlyWebClient extends WebClient {
    WindowsFriendlyWebClient(ProxyInfo proxyInfo,
                             CredentialsProvider credentialsProvider = getWindowsProvider()) {
        super()
        this.webConnection = new WindowsFriendlyWebConnection(this, proxyInfo)
        this.credentialsProvider = credentialsProvider
    }

    private static CredentialsProvider getWindowsProvider() {
        // normally this would be taken care of already by WinHttpClients.custom() in WindowsFriendlyWebConnection
        // but htmlunit overwrites that and we can't read from the builder
        // so need to set this explicitly
        new WindowsCredentialsProvider(new SystemDefaultCredentialsProvider())
    }
}
