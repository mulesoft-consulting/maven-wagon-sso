package com.mulesoft.maven.sso

import groovy.util.logging.Slf4j
import org.apache.http.HttpException
import org.apache.http.HttpHost
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.DateUtils
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.WinHttpClients
import org.apache.http.impl.conn.DefaultProxyRoutePlanner
import org.apache.http.message.BasicHeader
import org.apache.maven.wagon.*
import org.apache.maven.wagon.authentication.AuthenticationException
import org.apache.maven.wagon.authorization.AuthorizationException
import org.apache.maven.wagon.events.TransferEvent
import org.apache.maven.wagon.resource.Resource

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

@Slf4j
class WinSSOFriendlyHttpWagon extends StreamWagon {
    // this class is instantiated for each repository
    private CloseableHttpClient httpClient
    private HttpClientContext httpClientContext
    private boolean closed
    String samlIdpUrl
    String anypointProfileUrl

    private static final int MAX_BACKOFF_WAIT_SECONDS = Integer.parseInt(
            System.getProperty("maven.wagon.httpconnectionManager.maxBackoffSeconds", "180"))
    private int initialBackoffSeconds = Integer.parseInt(
            System.getProperty("maven.wagon.httpconnectionManager.backoffSeconds", "5"))
    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT")
    // map is more efficient to check than a list, value is meaningless
    private static final Map<Integer, Integer> getSuccessCodes = [
            200: 1
    ]
    private static final Map<Integer, Integer> postSuccessCodes = [
            200: 1,
            201: 1,
            202: 1,
            204: 1
    ]

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
        long timestamp = resource.getLastModified()
        if (timestamp > 0L) {
            SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss zzz", Locale.US)
            fmt.setTimeZone(GMT_TIME_ZONE)
            def hdr = new BasicHeader("If-Modified-Since", fmt.format(new Date(timestamp)))
            this.fireTransferDebug("sending ==> " + hdr + "(" + timestamp + ")")
            get.addHeader(hdr)
        }
        try {
            def response = httpClient.execute(get, httpClientContext)
            def validateResult = validateResponse(url,
                                                  resource,
                                                  response,
                                                  getSuccessCodes) {
                this.fillInputData(backoff(wait, url), inputData)
            }
            if (validateResult) {
                def entity = response.getEntity()
                if (entity != null) {
                    inputData.setInputStream(entity.getContent())
                }
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

    private boolean validateResponse(String url,
                                     Resource resource,
                                     CloseableHttpResponse response,
                                     Map<Integer, Integer> expectedSuccessCodes,
                                     Closure waitRetry) {
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
                waitRetry()
                break
            default:
                if (expectedSuccessCodes.containsKey(statusCode)) {
                    def contentLengthHeader = response.getFirstHeader("Content-Length")
                    if (contentLengthHeader != null) {
                        try {
                            long contentLength = Long.parseLong(contentLengthHeader.getValue())
                            resource.setContentLength(contentLength)
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
                    return true
                }
                this.cleanupGetTransfer(resource)
                TransferFailedException e = new TransferFailedException(
                        "Failed to transfer file: " + url + ". Return code is: " + statusCode + " " + reasonPhrase)
                this.fireTransferError(resource, e, TransferEvent.REQUEST_GET)
                throw e
        }
        return false
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
    void put(File source,
             String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        def resource = new Resource(resourceName)
        doPut(resource, source)
    }

    private void doPut(Resource resource,
                       File source,
                       int wait = initialBackoffSeconds) {
        def url = new URIBuilder(repository.url + "/" + resource).build().toString()
        def post = new HttpPost(url)
        post.entity = new InputStreamEntity(source.newInputStream())
        CloseableHttpResponse response = null
        try {
            response = httpClient.execute(post, httpClientContext)
            validateResponse(url,
                             resource,
                             response,
                             postSuccessCodes) {
                doPut(resource, source, backoff(wait, url))
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
        finally {
            response?.close()
        }
    }

    @Override
    void fillOutputData(OutputData outputData) throws TransferFailedException {
        throw new Exception('Not using this approach')
    }

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
        assert !httpClient: "Didn't expect to already have a client for ${repository}!"
        def builder = WinHttpClients.custom()
        // some proxies won't accept outbound traffic without a user agent
        builder.userAgent = 'AHC'
        def proxyInfo = getProxyInfo(repository.protocol, repository.host)
        if (proxyInfo) {
            // Maven takes care of deciding whether we need a proxy or not since this builder is just for
            // a single repository
            builder.routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxyInfo.host, proxyInfo.port))
        }
        httpClientContext = HttpClientContext.create()
        if (samlIdpUrl) {
            log.info 'Enabling Anypoint access token fetcher for repository {}',
                     this.repository
            def profileUrl = this.anypointProfileUrl ?: 'https://anypoint.mulesoft.com/accounts/api/profile'
            def accessTokenFetcher = new AccessTokenFetcher(proxyInfo,
                                                            profileUrl,
                                                            samlIdpUrl)
            httpClientContext.credentialsProvider = new AnypointTokenCredentialsProvider(
                    httpClientContext.credentialsProvider,
                    accessTokenFetcher)
        }
        httpClient = builder.build()
    }

    @Override
    void closeConnection() throws ConnectionException {
        if (!closed) {
            httpClient.close()
            closed = true
        }
    }

    @Override
    boolean resourceExists(String resourceName) throws TransferFailedException, AuthorizationException {
        println '-----------------------hey-------------'
        throw new UnsupportedOperationException("The wagon you are using has not implemented resourceExists()");
    }
}
