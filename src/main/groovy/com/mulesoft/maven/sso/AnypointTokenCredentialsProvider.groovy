package com.mulesoft.maven.sso

import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider

class AnypointTokenCredentialsProvider implements CredentialsProvider {
    private final CredentialsProvider existingProvider
    private final AccessTokenFetcher accessTokenFetcher

    AnypointTokenCredentialsProvider(CredentialsProvider existingProvider,
                                     AccessTokenFetcher accessTokenFetcher) {
        this.accessTokenFetcher = accessTokenFetcher
        this.existingProvider = existingProvider
        println "existing provider ${existingProvider}"
    }

    // TODO: If it's not for our specific basic auth server, delegate to existingProvider if it exists
    @Override
    void setCredentials(AuthScope authScope, Credentials credentials) {
        println "set creds ${authScope} ${credentials}"
    }

    // TODO: If it's not for our specific basic auth server, delegate to existingProvider
    @Override
    Credentials getCredentials(AuthScope authScope) {
        println "get creds ${authScope}"
        new UsernamePasswordCredentials('the_user', 'thePassword')
    }

    // TODO: If it's not for our specific basic auth server, delegate to existingProvider
    @Override
    void clear() {

    }
}
