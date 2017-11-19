package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.WebClient
import groovy.json.JsonSlurper
import org.apache.maven.wagon.proxy.ProxyInfo

class AccessTokenFetcher {
    private final WebClient client
    private final String anypointProfileUrl
    private final String samlIdpUrl

    AccessTokenFetcher(ProxyInfo proxyInfo,
                       String anypointProfileUrl,
                       String samlIdpUrl) {
        this.samlIdpUrl = samlIdpUrl
        this.anypointProfileUrl = anypointProfileUrl
        client = new WebClient()
        client.webConnection = new HtmlUnitCustomizedWebConnection(this.client, proxyInfo)
    }

    String getAccessToken() {
        println "Triggering initial SAML flow with ${anypointProfileUrl}"
        client.getPage(samlIdpUrl)
        println "SAML Flow complete, now fetching access token from ${anypointProfileUrl}"
        def jsonProfile = client.getPage(anypointProfileUrl)
        def map = new JsonSlurper().parse(jsonProfile.webResponse.contentAsStream)
        def token = map['access_token']
        assert token: "Unable to find access token in ${map}"
        println 'Access token fetched'
        token
    }
}
