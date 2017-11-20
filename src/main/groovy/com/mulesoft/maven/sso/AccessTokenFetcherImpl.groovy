package com.mulesoft.maven.sso

import com.gargoylesoftware.htmlunit.HttpMethod
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.WebRequest
import com.gargoylesoftware.htmlunit.html.HtmlInput
import com.gargoylesoftware.htmlunit.html.HtmlPage
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.message.BasicNameValuePair
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
        client.options.javaScriptEnabled = false
    }

    String getAccessToken() {
        log.info "Triggering initial SAML flow with {}",
                 samlIdpUrl
        def responsePage = client.getPage(samlIdpUrl)
        handleSamlResponse(responsePage)
        log.info "SAML Flow complete, now fetching access token from {}",
                 anypointProfileUrl
        def jsonProfile = client.getPage(anypointProfileUrl)
        def map = new JsonSlurper().parse(jsonProfile.webResponse.contentAsStream)
        def token = map['access_token']
        assert token: "Unable to find access token in ${map}"
        log.info "Access token fetched, successfully authenticated as '{}'.",
                 map['username']
        token
    }

    private void handleSamlResponse(Page responsePage) {
        assert responsePage.htmlPage: "Expected ${responsePage.url} to be a web page!"
        assert responsePage instanceof HtmlPage
        def samlForm = responsePage.forms.find { form ->
            form.getInputsByName('SAMLResponse').any()
        }
        assert samlForm: "Expected a SAML form in response! ${responsePage.webResponse.contentAsString}"
        def request = new WebRequest(samlForm.actionAttribute.toURL())
        request.httpMethod = HttpMethod.POST
        def params = samlForm.childElements
                .findAll { element -> element instanceof HtmlInput }
                .collect { HtmlInput input ->
            new BasicNameValuePair(input.nameAttribute,
                                   input.valueAttribute)
        }
        def entity = new UrlEncodedFormEntity(params)
        request.requestBody = entity.content.text
        def header = entity.contentType
        request.setAdditionalHeader(header.name,
                                    header.value)
        client.getPage(request)
    }
}
