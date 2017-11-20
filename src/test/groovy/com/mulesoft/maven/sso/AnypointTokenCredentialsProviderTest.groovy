package com.mulesoft.maven.sso

import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.maven.wagon.repository.Repository
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class AnypointTokenCredentialsProviderTest {
    private AnypointTokenCredentialsProvider getProvider(CredentialsProvider existing = null) {
        new AnypointTokenCredentialsProvider(existing)
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
    void setCredentials_basicScheme() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('other.repo.url',
                                      8080,
                                      'realm',
                                      'Basic')
        def creds = new UsernamePasswordCredentials('user', 'pass')

        // act
        provider.setCredentials(authScope, creds)

        // assert
        assertThat provider.getCredentials(authScope),
                   is(equalTo(creds))
    }

    @Test
    void getCredentials_existingProviderAvailable_notOurScheme() {
        // arrange
        def existing = [
                getCredentials: { AuthScope scope ->
                    new UsernamePasswordCredentials('user', 'pass')
                }
        ] as CredentialsProvider
        def provider = getProvider(existing)
        def authScope = new AuthScope('our.repo.url',
                                      8080,
                                      'realm',
                                      'NEGOTIATE')
        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result,
                   is(equalTo(new UsernamePasswordCredentials('user', 'pass')))
    }

    @Test
    void getCredentials_noAccessToken_notOurHostName() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('other.repo.url',
                                      8080,
                                      'realm',
                                      'Basic')
        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result,
                   is(nullValue())
    }

    @Test
    void getCredentials_noAccessToken_notOurPort() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'Basic')
        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result,
                   is(nullValue())
    }

    @Test
    void getCredentials_notOurPort_AlreadyStored() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'Basic')
        def creds = new UsernamePasswordCredentials('user', 'pass')
        provider.setCredentials(authScope, creds)

        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result,
                   is(equalTo(creds))
    }

    @Test
    void getCredentials_Us_Not_Yet_Fetched() {
        // arrange
        def fetched = false
        def tokenFetcher = [
                getAccessToken: {
                    assert !fetched: 'Already fetched!'
                    fetched = true
                    'abc'
                }
        ] as AccessTokenFetcher
        def provider = getProvider(null)
        provider.addAccessTokenFetcher(new Repository('the_id',
                                                      'http://our.repo.url:8080'),
                                       tokenFetcher)
        def authScope = new AuthScope('our.repo.url',
                                      8080,
                                      'realm',
                                      'Basic')

        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result.userPrincipal.name,
                   is(equalTo('~~~Token~~~'))
        assertThat result.password,
                   is(equalTo('abc'))
        provider.getCredentials(authScope)
    }

    @Test
    void getCredentials_Us_DefaultPort() {
        // arrange
        def fetched = false
        def tokenFetcher = [
                getAccessToken: {
                    assert !fetched: 'Already fetched!'
                    fetched = true
                    'abc'
                }
        ] as AccessTokenFetcher
        def provider = getProvider(null)
        provider.addAccessTokenFetcher(new Repository('the_id',
                                                      'http://our.repo.url:-1'),
                                       tokenFetcher)
        def authScope = new AuthScope('our.repo.url',
                                      80,
                                      'realm',
                                      'Basic')

        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result.userPrincipal.name,
                   is(equalTo('~~~Token~~~'))
        assertThat result.password,
                   is(equalTo('abc'))
        provider.getCredentials(authScope)
    }

    @Test
    void getCredentials_Not_Configured() {
        // arrange
        def fetched = false
        def tokenFetcher = [
                getAccessToken: {
                    assert !fetched: 'Already fetched!'
                    fetched = true
                    'abc'
                }
        ] as AccessTokenFetcher
        def provider = getProvider(null)
        provider.addAccessTokenFetcher(new Repository('the_id',
                                                      'http://our.repo.url:8080'),
                                       tokenFetcher)
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'Basic')

        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result,
                   is(nullValue())
    }

    @Test
    void getCredentials_Us_Already_Fetched() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('our.repo.url',
                                      8080,
                                      'realm',
                                      'Basic')
        def credentials = new UsernamePasswordCredentials('user', 'pass')
        provider.setCredentials(authScope,
                                credentials)

        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result,
                   is(equalTo(credentials))
    }

    @Test
    void getCredentials_Expired() {
        // arrange
        def tokenFetcher = [
                getAccessToken: {
                    'def'
                }
        ] as AccessTokenFetcher
        def provider = getProvider()
        provider.addAccessTokenFetcher(new Repository('the_id',
                                                      'http://our.repo.url:8080'),
                                       tokenFetcher)
        def authScope = new AuthScope('our.repo.url',
                                      8080,
                                      'realm',
                                      'Basic')
        def credentials = new AccessTokenCredentials('abc',
                                                     500)
        provider.setCredentials(authScope,
                                credentials)

        // act
        def result = provider.getCredentials(authScope)

        // assert
        assertThat result.password,
                   is(equalTo('def'))
    }

    @Test
    void clear_noExisting() {
        // arrange
        def provider = getProvider()
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'Basic')
        def credentials = new UsernamePasswordCredentials('user', 'pass')
        provider.setCredentials(authScope,
                                credentials)

        // act
        provider.clear()

        // assert
        assertThat provider.getCredentials(authScope),
                   is(nullValue())
    }

    @Test
    void clear_existing_and_super() {
        // arrange
        def existingCleared = false
        def existing = [
                getCredentials: { AuthScope scope ->
                    null
                },
                clear         : {
                    existingCleared = true
                }
        ] as CredentialsProvider
        def provider = getProvider(existing)
        def authScope = new AuthScope('our.repo.url',
                                      8081,
                                      'realm',
                                      'Basic')
        def credentials = new UsernamePasswordCredentials('user', 'pass')
        provider.setCredentials(authScope,
                                credentials)

        // act
        provider.clear()

        // assert
        assertThat provider.getCredentials(authScope),
                   is(nullValue())
        assertThat existingCleared,
                   is(equalTo(true))
    }
}
