package com.mulesoft.maven.sso

import groovy.util.logging.Slf4j
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.AuthSchemes
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.maven.wagon.repository.Repository

@Slf4j
class AnypointTokenCredentialsProvider extends BasicCredentialsProvider {
    private final CredentialsProvider existingProvider
    private final static String BASIC_AUTH = AuthSchemes.BASIC.toUpperCase()
    private final Map<String, AccessTokenFetcher> fetchers = [:]

    AnypointTokenCredentialsProvider(CredentialsProvider existingProvider = new BasicCredentialsProvider()) {
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
        def key = getKey(repository.host,
                         repository.port)
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
            if (existingCreds) {
                return existingCreds
            }
            def key = getKey(authScope.host,
                             authScope.port)
            def accessTokenFetcher = fetchers[key]
            if (accessTokenFetcher) {
                log.info 'Existing credentials not available for {}, fetching',
                         key
                def credentials = new AccessTokenCredentials(accessTokenFetcher.accessToken, new Date())
                this.setCredentials(authScope, credentials)
                return credentials
            }
        }
        if (existingProvider) {
            existingProvider.getCredentials(authScope)
        }
    }

    @Override
    void clear() {
        super.clear()
        if (existingProvider) {
            existingProvider.clear()
        }
    }
}
