package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.WebClient
import org.apache.maven.wagon.proxy.ProxyInfo

class AccessTokenFetcher {
    private final WebClient client
    private final String anypointProfileUrl

    AccessTokenFetcher(ProxyInfo proxyInfo,
                       String anypointProfileUrl = 'https://anypoint.mulesoft.com/accounts/api/profile') {
        this.anypointProfileUrl = anypointProfileUrl
        client = new WebClient()
        client.webConnection = new HtmlUnitCustomizedWebConnection(this.client, proxyInfo)
    }

    String getAccessToken(String samlIdpUrl) {
        println "Triggering initial SAML flow with ${anypointProfileUrl}"
        client.getPage(samlIdpUrl)
        println "SAML Flow complete, now fetching access token from ${anypointProfileUrl}"
        def jsonProfile = client.getPage(anypointProfileUrl)
    }
}
