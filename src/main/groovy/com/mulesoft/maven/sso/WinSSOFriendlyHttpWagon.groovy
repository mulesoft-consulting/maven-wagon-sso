package com.mulesoft.maven.sso

import org.apache.http.HttpException
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.DateUtils
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.WinHttpClients
import org.apache.maven.wagon.*
import org.apache.maven.wagon.authentication.AuthenticationException
import org.apache.maven.wagon.authorization.AuthorizationException
import org.apache.maven.wagon.events.TransferEvent

import java.util.concurrent.TimeUnit

class WinSSOFriendlyHttpWagon extends StreamWagon {
    // this class is instantiated for each repository
    private CloseableHttpClient httpClient
    private boolean closed
    String samlIdpUrl
    private static final int MAX_BACKOFF_WAIT_SECONDS = Integer.parseInt(
            System.getProperty("maven.wagon.httpconnectionManager.maxBackoffSeconds", "180"))
    private int initialBackoffSeconds = Integer.parseInt(
            System.getProperty("maven.wagon.httpconnectionManager.backoffSeconds", "5"))

    @Override
    void fillInputData(InputData inputData)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        fillInputData(initialBackoffSeconds, inputData)
    }

    void fillInputData(int wait,
                       InputData inputData) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        def resource = inputData.resource
        def url = new URIBuilder(repository.url + "/" + resource).build().toString()
        def get = new HttpGet(url)
        try {
            def response = httpClient.execute(get)
            def statusCode = response.getStatusLine().getStatusCode()
            def reasonPhrase = ", ReasonPhrase:" + response.getStatusLine().getReasonPhrase() + "."
            this.fireTransferDebug(url + " - Status code: " + statusCode + reasonPhrase)
            switch (statusCode) {
                case 304:
                    break
                case 401:
                    this.fireSessionConnectionRefused()
                    throw new AuthorizationException("Not authorized " + reasonPhrase)
                case 403:
                    this.fireSessionConnectionRefused()
                    throw new AuthorizationException("Access denied to: " + url + " " + reasonPhrase)
                case 404:
                    throw new ResourceDoesNotExistException("File: " + url + " " + reasonPhrase)
                case 407:
                    this.fireSessionConnectionRefused()
                    throw new AuthorizationException("Not authorized by proxy " + reasonPhrase)
                case 429:
                    this.fillInputData(backoff(wait, url), inputData)
                    break
                case 200:
                    def contentLengthHeader = response.getFirstHeader("Content-Length")
                    if (contentLengthHeader != null) {
                        try {
                            long contentLength = Long.parseLong(contentLengthHeader.getValue())
                            // resource.setContentLength(contentLength)
                        } catch (NumberFormatException var17) {
                            this.fireTransferDebug(
                                    "error parsing content length header '" + contentLengthHeader.getValue() + "' " + var17)
                        }
                    }

                    def lastModifiedHeader = response.getFirstHeader("Last-Modified")
                    if (lastModifiedHeader != null) {
                        Date lastModified = DateUtils.parseDate(lastModifiedHeader.getValue())
                        if (lastModified != null) {
                            resource.setLastModified(lastModified.getTime())
                            this.fireTransferDebug(
                                    "last-modified = " + lastModifiedHeader.getValue() + " (" + lastModified.getTime() + ")")
                        }
                    }

                    def entity = response.getEntity()
                    if (entity != null) {
                        inputData.setInputStream(entity.getContent())
                    }
                    break
                default:
                    this.cleanupGetTransfer(resource)
                    TransferFailedException e = new TransferFailedException(
                            "Failed to transfer file: " + url + ". Return code is: " + statusCode + " " + reasonPhrase)
                    this.fireTransferError(resource, e, TransferEvent.REQUEST_GET)
                    throw e
            }
        }

        catch (IOException var18) {
            this.fireTransferError(resource, var18, TransferEvent.REQUEST_GET)
            throw new TransferFailedException(var18.getMessage(), var18)
        } catch (HttpException var19) {
            this.fireTransferError(resource, var19, TransferEvent.REQUEST_GET)
            throw new TransferFailedException(var19.getMessage(), var19)
        } catch (InterruptedException var20) {
            this.fireTransferError(resource, var20, TransferEvent.REQUEST_GET)
            throw new TransferFailedException(var20.getMessage(), var20)
        }
    }

    protected static int backoff(int wait, String url) throws InterruptedException, TransferFailedException {
        TimeUnit.SECONDS.sleep((long) wait)
        int nextWait = wait * 2
        if (nextWait >= MAX_BACKOFF_WAIT_SECONDS) {
            throw new TransferFailedException("Waited too long to access: " + url + ". Return code is: " + 429)
        } else {
            return nextWait
        }
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
        // TODO: Add proxy config/route planner here
        // TODO: Also need to add an interceptor to get the SAML token, etc. when appropriate
        // TODO: Need to figure out how to set basic auth credentials for every request w/ httpclient
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
