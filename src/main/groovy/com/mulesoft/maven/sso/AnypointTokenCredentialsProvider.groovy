package com.mulesoft.maven.sso

import groovy.util.logging.Slf4j
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.auth.win.WindowsCredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.SystemDefaultCredentialsProvider
import org.apache.http.impl.client.WinHttpClients
import org.apache.http.impl.conn.DefaultSchemePortResolver
import org.apache.maven.wagon.repository.Repository

@Slf4j
class AnypointTokenCredentialsProvider implements CredentialsProvider {
    private final CredentialsProvider baselineProvider
    private final Map<String, AccessTokenFetcher> fetchers = [:]
    // 2 hours
    private final static long DEFAULT_TOKEN_TIME_MS = Long.parseLong(
            System.getProperty('anypoint.token.timeout.ms', (2 * 60 * 60 * 1000).toString())
    )
    private final long maxTokenLifeInMs

    AnypointTokenCredentialsProvider(CredentialsProvider baselineProvider = getBaselineProvider(),
                                     long maxTokenLifeInMs = DEFAULT_TOKEN_TIME_MS) {
        this.maxTokenLifeInMs = maxTokenLifeInMs
        this.baselineProvider = baselineProvider
    }

    private static CredentialsProvider getBaselineProvider() {
        // can't retrieve credentialsprovider from builder, so do it this way
        // WindowsNegotiateScheme doesn't actually use these, but best to be consistent
        // in case that changes at some point
        WinHttpClients.isWinAuthAvailable() ?
                new WindowsCredentialsProvider(new SystemDefaultCredentialsProvider()) :
                new BasicCredentialsProvider()
    }

    private static String getKey(String host,
                                 int port) {
        "${host}:${port}"
    }

    void addAccessTokenFetcher(Repository repository,
                               AccessTokenFetcher accessTokenFetcher) {
        def port = repository.port
        def host = repository.host
        // -1 means the default port is being used according to the Maven repository, but we need
        // it to be specific for authscope/httpclient to work properly
        if (port == -1) {
            def resolver = new DefaultSchemePortResolver()
            port = resolver.resolve(new HttpHost(host, port, repository.protocol))
        }
        def key = getKey(host,
                         port)
        // can't use URL, don't know full URL for auth scopes
        fetchers[key] = accessTokenFetcher
    }

    @Override
    void setCredentials(AuthScope authScope, Credentials credentials) {
        baselineProvider.setCredentials(authScope, credentials)
    }

    @Override
    Credentials getCredentials(AuthScope authScope) {
        def existingCreds = baselineProvider.getCredentials(authScope)
        def expired = existingCreds &&
                (existingCreds instanceof AccessTokenCredentials) &&
                existingCreds.isExpired(maxTokenLifeInMs)
        if (existingCreds && !expired) {
            return existingCreds
        }
        def key = getKey(authScope.host,
                         authScope.port)
        if (fetchers.containsKey(key)) {
            def credentials = getCredentialFromAccessTokenFetcher(expired,
                                                                  key)
            // don't want to have to re-fetch the access token over and over again
            // so cache it by setting it
            this.setCredentials(authScope, credentials)
            return credentials
        }
        return null
    }

    private Credentials getCredentialFromAccessTokenFetcher(boolean expired,
                                                            String key) {
        def accessTokenFetcher = fetchers[key]
        def message = expired ? 'Token for {} has expired, fetching a new one' :
                'Existing credentials not available for {}, fetching first one'
        log.info message, key
        new AccessTokenCredentials(accessTokenFetcher.accessToken,
                                   System.currentTimeMillis())
    }

    @Override
    void clear() {
        baselineProvider.clear()
    }
}
