package com.mulesoft.maven.sso

import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
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

    @Override
    void setCredentials(AuthScope authScope, Credentials credentials) {
        if (existingProvider) {
            existingProvider.setCredentials(authScope, credentials)
        }
    }

    // TODO: If it's not for our specific basic auth server, delegate to existingProvider
    @Override
    Credentials getCredentials(AuthScope authScope) {
        // TODO: First check map, if not there, then use fetcher to get it and then set it
        println "get creds ${authScope}"
        new UsernamePasswordCredentials('~~~Token~~~', 'abcdef')
    }

    // TODO: clear our own and delegate to existingProvider if it exists
    @Override
    void clear() {

    }
}
