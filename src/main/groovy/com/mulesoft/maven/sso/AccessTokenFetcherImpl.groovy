package com.mulesoft.maven.sso

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.http.HttpStatus
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.WinHttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.maven.wagon.proxy.ProxyInfo
import org.jsoup.Jsoup

@Slf4j
class AccessTokenFetcherImpl implements AccessTokenFetcher {
    private final String anypointProfileUrl
    private final String samlIdpUrl
    private final HttpClientBuilder clientBuilder

    AccessTokenFetcherImpl(ProxyInfo proxyInfo,
                           String anypointProfileUrl,
                           String samlIdpUrl) {
        this.samlIdpUrl = samlIdpUrl
        this.anypointProfileUrl = anypointProfileUrl
        clientBuilder = WinHttpClients.custom()
        if (proxyInfo) {
            clientBuilder.routePlanner = new WagonProxyInfoRoutePlanner(proxyInfo)
        }
    }

    String getAccessToken() {
        log.info "Triggering initial SAML flow with {}",
                 samlIdpUrl
        CloseableHttpClient client = null
        CloseableHttpResponse response = null
        try {
            client = clientBuilder.build()
            def get = new HttpGet(samlIdpUrl)
            response = client.execute(get)
            handleSamlResponse(response,
                               client,
                               samlIdpUrl)
            response.close()
            response = null
            // now we've done the equivalent of taking our browser all the way to the anypoint page, should have
            // enough cookies to access the profile page, where we can grab a token
            log.info "SAML Flow complete, now fetching access token from {}",
                     anypointProfileUrl
            get = new HttpGet(anypointProfileUrl)
            response = client.execute(get)
            def statusLine = response.statusLine
            assert statusLine.statusCode == HttpStatus.SC_OK: "While trying to fetch profile/JSON, content type was not what was exoected - ${statusLine.reasonPhrase}"
            assert response.getLastHeader('Content-Type').value == 'application/json'
            def map = new JsonSlurper().parse(response.entity.content)
            def token = map['access_token']
            assert token: "Unable to find access token in ${map}"
            log.info "Access token fetched, successfully authenticated as '{}'.",
                     map['username']
            token
        }
        finally {
            if (response) {
                response.close()
            }
            client.close()
        }
    }

    private static void handleSamlResponse(CloseableHttpResponse response,
                                           CloseableHttpClient client,
                                           String url) {
        def statusLine = response.statusLine
        assert statusLine.statusCode == HttpStatus.SC_OK: "While trying to fetch SAML IDP URL - ${statusLine.reasonPhrase}"
        def parsedDocument = Jsoup.parse(response.entity.content,
                                         'utf-8',
                                         url)
        def samlForm = parsedDocument.getElementsByTag('form')
                .find { form ->
            form.getElementsByAttributeValue('name', 'SAMLResponse').any()
        }
        assert samlForm: "Expected a SAML form in response! ${parsedDocument}"
        def parameters = samlForm.getElementsByTag('input').collect { input ->
            new BasicNameValuePair(input.attr('name'),
                                   input.attr('value'))
        }
        def post = new HttpPost(samlForm.attr('action'))
        log.info 'Posting SAML assertions back to {}',
                 post.getURI()
        post.entity = new UrlEncodedFormEntity(parameters)
        response = client.execute(post)
        statusLine = response.statusLine
        try {
            assert statusLine.statusCode == HttpStatus.SC_OK: "While trying to post SAML assertion - ${statusLine.reasonPhrase}"
        }
        finally {
            response.close()
        }
    }
}
