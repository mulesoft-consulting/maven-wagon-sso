package com.mulesoft.maven.sso

import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.WinHttpClients
import org.apache.maven.wagon.*
import org.apache.maven.wagon.authentication.AuthenticationException
import org.apache.maven.wagon.authorization.AuthorizationException

class WinSSOFriendlyHttpWagon extends StreamWagon {
    // this class is instantiated for each repository
    private CloseableHttpClient httpClient
    private boolean closed
    String samlIdpUrl

    @Override
    void fillInputData(
            InputData inputData) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        println "in theory we would execute a request here for - ${inputData.resource}"
    }

    @Override
    void fillOutputData(OutputData outputData) throws TransferFailedException {
        throw new Exception('This wagon provider should not use this!')
    }

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
        assert !httpClient: "Didn't expect to already have a client for ${repository}!"
        def builder = WinHttpClients.custom()
        // some proxies won't accept outbound traffic without a user agent
        builder.userAgent = 'AHC'
        def proxyInfo = getProxyInfo()
        // this will get set by doing this, can control which repos we try and do SAML idp stuff for
//        <configuration>
//        <samlIdpUrl>true</samlIdpUrl>
//        </configuration>
        println "repo param ${samlIdpUrl}"
        println "proxy info for repo ${repository} is ${proxyInfo}"
        // TODO: Add proxy config/route planner here
        // TODO: Also need to add an interceptor to get the SAML token, etc. when appropriate
        // TODO: Need to figure out how to set basic auth credentials for every request w/ httpclient
        httpClient = builder.build()
    }

    @Override
    void closeConnection() throws ConnectionException {
        if (!closed) {
            println "Closing client for repo ${repository}"
            httpClient.close()
            closed = true
        }
    }
}
