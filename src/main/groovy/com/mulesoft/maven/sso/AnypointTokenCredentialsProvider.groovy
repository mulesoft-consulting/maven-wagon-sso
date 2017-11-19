package com.mulesoft.maven.sso

import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.maven.wagon.repository.Repository

class AnypointTokenCredentialsProvider extends BasicCredentialsProvider {
    private final CredentialsProvider existingProvider
    private final AccessTokenFetcher accessTokenFetcher
    private final Repository anypointRepositoryInfo

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
                repoInfo.port == authScope.port &&
                authScope.scheme == 'BASIC'
    }

    @Override
    void setCredentials(AuthScope authScope, Credentials credentials) {
        if (isOurs(authScope)) {
            super.setCredentials(authScope, credentials)
        } else if (existingProvider) {
            existingProvider.setCredentials(authScope, credentials)
        }
    }
    
    @Override
    Credentials getCredentials(AuthScope authScope) {
        if (isOurs(authScope)) {
            return super.getCredentials(authScope)
        }
        if (existingProvider) {
            existingProvider.getCredentials(authScope)
        }
        //new UsernamePasswordCredentials('~~~Token~~~', 'abcdef')
    }

    // TODO: clear our own and delegate to existingProvider if it exists
    @Override
    void clear() {

    }
}
