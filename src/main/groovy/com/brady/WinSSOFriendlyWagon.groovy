package com.brady

import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.WinHttpClients
import org.apache.maven.wagon.*
import org.apache.maven.wagon.authentication.AuthenticationException
import org.apache.maven.wagon.authorization.AuthorizationException

class WinSSOFriendlyWagon extends StreamWagon {
    // this class is instantiated for each repo
    private CloseableHttpClient httpClient
    private boolean closed

    @Override
    void fillInputData(
            InputData inputData) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        println "in theory we would do something in inputData here for - ${inputData.resource}"
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
        httpClient = builder.build()
    }

    @Override
    void closeConnection() throws ConnectionException {
        if (!closed) {
            httpClient.close()
            closed = true
        }
    }
}
