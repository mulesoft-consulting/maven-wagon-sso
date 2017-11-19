package com.mulesoft.maven.sso

import groovy.util.logging.Slf4j
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.AuthSchemes
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.maven.wagon.repository.Repository

@Slf4j
class AnypointTokenCredentialsProvider extends BasicCredentialsProvider {
    private final CredentialsProvider existingProvider
    private final AccessTokenFetcher accessTokenFetcher
    private final Repository anypointRepositoryInfo
    private final static String BASIC_AUTH = AuthSchemes.BASIC.toUpperCase()
    // https://docs.mulesoft.com/anypoint-exchange/to-publish-assets-maven#to-publish-federated-assets
    private final static String ANYPOINT_TOKEN_VIA_BASIC = '~~~Token~~~'

    AnypointTokenCredentialsProvider(CredentialsProvider existingProvider,
                                     AccessTokenFetcher accessTokenFetcher,
                                     Repository anypointRepositoryInfo) {
        this.anypointRepositoryInfo = anypointRepositoryInfo
        this.accessTokenFetcher = accessTokenFetcher
        this.existingProvider = existingProvider
    }

    private boolean isOurs(AuthScope authScope) {
        def repoInfo = this.anypointRepositoryInfo
        repoInfo.host == authScope.host &&
                repoInfo.port == authScope.port

    }

    private static boolean isBasicAuth(AuthScope authScope) {
        authScope.scheme == BASIC_AUTH
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
            if (isOurs(authScope)) {
                log.info 'Existing credentials not available for {}, fetching',
                         this.anypointRepositoryInfo
                def credentials = new UsernamePasswordCredentials(ANYPOINT_TOKEN_VIA_BASIC,
                                                                  this.accessTokenFetcher.accessToken)
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
