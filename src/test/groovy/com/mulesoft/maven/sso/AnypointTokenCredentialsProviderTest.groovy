package com.mulesoft.maven.sso

import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.maven.wagon.repository.Repository
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class AnypointTokenCredentialsProviderTest {
    private AnypointTokenCredentialsProvider getProvider(CredentialsProvider existing = null,
                                                         AccessTokenFetcher tokenFetcher = null) {
        def repo = new Repository('the-repo', 'http://our.repo.url:8080')
        new AnypointTokenCredentialsProvider(existing,
                                             tokenFetcher,
                                             repo)
    }

    @Test
    void setCredentials_existingProviderAvailable_notOurScheme() {
        // arrange
        AuthScope existingSetAuth = null
        Credentials existingSetCreds = null
        def existing = [
                setCredentials: { AuthScope scope, Credentials creds ->
                    existingSetAuth = scope
                    existingSetCreds = creds

                }
        ] as CredentialsProvider
        def provider = getProvider(existing)
        def authScope = new AuthScope('our.repo.url',
                                      8080,
                                      'realm',
                                      'NEGOTIATE')
        def creds = new UsernamePasswordCredentials('user', 'pass')

        // act
        provider.setCredentials(authScope, creds)

        // assert
        assertThat existingSetAuth,
                   is(equalTo(authScope))
        assertThat existingSetCreds,
                   is(equalTo(creds))
    }

    @Test
    void setCredentials_existingProviderAvailable_notOurHostName() {
        // arrange
        AuthScope existingSetAuth = null
        Credentials existingSetCreds = null
        def existing = [
                setCredentials: { AuthScope scope, Credentials creds ->
                    existingSetAuth = scope
                    existingSetCreds = creds

                }
        ] as CredentialsProvider
        def provider = getProvider(existing)
        def authScope = new AuthScope('other.repo.url',
                                      8080,
                                      'realm',
                                      'BASIC')
        def creds = new UsernamePasswordCredentials('user', 'pass')

        // act
        provider.setCredentials(authScope, creds)

        // assert
        assertThat existingSetAuth,
                   is(equalTo(authScope))
        assertThat existingSetCreds,
                   is(equalTo(creds))
    }

    @Test
    void setCredentials_existingProviderAvailable_notOurPort() {
        // arrange
        AuthScope existingSetAuth = null
        Credentials existingSetCreds = null
        def existing = [
                setCredentials: { AuthScope scope, Credentials creds ->
                    existingSetAuth = scope
                    existingSetCreds = creds

                }
        ] as CredentialsProvider
        def provider = getProvider(existing)
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'BASIC')
        def creds = new UsernamePasswordCredentials('user', 'pass')

        // act
        provider.setCredentials(authScope, creds)

        // assert
        assertThat existingSetAuth,
                   is(equalTo(authScope))
        assertThat existingSetCreds,
                   is(equalTo(creds))
    }

    @Test
    void setCredentials_noExistingProviderAvailable_notUs() {
        def provider = getProvider()
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'BASIC')
        def creds = new UsernamePasswordCredentials('user', 'pass')

        // act
        provider.setCredentials(authScope, creds)

        // assert
        // should not crash
    }

    @Test
    void setCredentials_Us() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('our.repo.url',
                                      8080,
                                      'realm',
                                      'BASIC')
        def creds = new UsernamePasswordCredentials('user', 'pass')

        // act
        provider.setCredentials(authScope, creds)

        // assert
        assertThat provider.getCredentials(authScope),
                   is(equalTo(creds))
    }
}
