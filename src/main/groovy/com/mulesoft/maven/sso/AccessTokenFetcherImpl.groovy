package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.WebWindowEvent
import com.gargoylesoftware.htmlunit.WebWindowListener
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.maven.wagon.proxy.ProxyInfo

@Slf4j
class AccessTokenFetcherImpl implements AccessTokenFetcher {
    private final WebClient client
    private final String anypointProfileUrl
    private final String samlIdpUrl

    AccessTokenFetcherImpl(ProxyInfo proxyInfo,
                           String anypointProfileUrl,
                           String samlIdpUrl) {
        this.samlIdpUrl = samlIdpUrl
        this.anypointProfileUrl = anypointProfileUrl
        client = new WindowsFriendlyWebClient(proxyInfo)
        client.options.cssEnabled = false
    }

    String getAccessToken() {
        log.info "Triggering initial SAML flow with {}",
                 samlIdpUrl
        client.getPage(samlIdpUrl)
        log.info "SAML Flow complete, now fetching access token from {}",
                 anypointProfileUrl
        println "cookies are ${client.cookieManager.cookies}"
        def jsonProfile = client.getPage(anypointProfileUrl)
        def map = new JsonSlurper().parse(jsonProfile.webResponse.contentAsStream)
        def token = map['access_token']
        assert token: "Unable to find access token in ${map}"
        log.info "Access token fetched, successfully authenticated as '{}'.",
                 map['username']
        token
    }
}
