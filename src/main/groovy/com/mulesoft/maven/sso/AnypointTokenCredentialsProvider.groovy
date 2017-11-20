package com.mulesoft.maven.sso

import groovy.util.logging.Slf4j
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.AuthSchemes
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.conn.DefaultSchemePortResolver
import org.apache.maven.wagon.repository.Repository

@Slf4j
class AnypointTokenCredentialsProvider extends BasicCredentialsProvider {
    private final CredentialsProvider existingProvider
    private final static String BASIC_AUTH = AuthSchemes.BASIC.toUpperCase()
    private final Map<String, AccessTokenFetcher> fetchers = [:]
    // 2 hours
    private final static long DEFAULT_TOKEN_TIME_MS = Long.parseLong(
            System.getProperty('anypoint.token.timeout.ms', (2 * 60 * 60 * 1000).toString())
    )
    private final long maxTokenLifeInMs

    AnypointTokenCredentialsProvider(CredentialsProvider existingProvider = new BasicCredentialsProvider(),
                                     long maxTokenLifeInMs = DEFAULT_TOKEN_TIME_MS) {
        this.maxTokenLifeInMs = maxTokenLifeInMs
        this.existingProvider = existingProvider
    }

    private static boolean isBasicAuth(AuthScope authScope) {
        authScope.scheme == BASIC_AUTH
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
        if (isBasicAuth(authScope)) {
            super.setCredentials(authScope, credentials)
        } else if (existingProvider) {
            existingProvider.setCredentials(authScope, credentials)
        }
    }

    @Override
    Credentials getCredentials(AuthScope authScope) {
        if (isBasicAuth(authScope)) {
            def existingCreds = super.getCredentials(authScope)
            def expired = existingCreds &&
                    (existingCreds instanceof AccessTokenCredentials) &&
                    existingCreds.isExpired(maxTokenLifeInMs)
            if (existingCreds && !expired) {
                return existingCreds
            }
            def key = getKey(authScope.host,
                             authScope.port)
            if (fetchers.containsKey(key)) {
                return getCredentialFromAccessTokenFetcher(expired,
                                                           key,
                                                           authScope)
            }
        }
        if (existingProvider) {
            existingProvider.getCredentials(authScope)
        }
    }

    private AccessTokenCredentials getCredentialFromAccessTokenFetcher(boolean expired,
                                                                       String key,
                                                                       AuthScope authScope) {
        def accessTokenFetcher = fetchers[key]
        def message = expired ? 'Token for {} has expired, fetching a new one' :
                'Existing credentials not available for {}, fetching first one'
        log.info message, key
        def credentials = new AccessTokenCredentials(accessTokenFetcher.accessToken,
                                                     System.currentTimeMillis())
        this.setCredentials(authScope, credentials)
        return credentials
    }

    @Override
    void clear() {
        super.clear()
        if (existingProvider) {
            existingProvider.clear()
        }
    }
}
