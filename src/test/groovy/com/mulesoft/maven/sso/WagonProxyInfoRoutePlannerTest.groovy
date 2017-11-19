package com.mulesoft.maven.sso

import org.apache.maven.wagon.proxy.ProxyInfo
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class WagonProxyInfoRoutePlannerTest {
    @Test
    void direct() {
        // arrange
        WagonProxyInfoRoutePlanner planner = getPlanner()

        // act
        def result = planner.isDirect('localhost')

        // assert
        assertThat result,
                   is(equalTo(true))
    }

    private static WagonProxyInfoRoutePlanner getPlanner() {
        def proxyInfo = new ProxyInfo()
        proxyInfo.host = 'the_proxy'
        proxyInfo.port = 8080
        proxyInfo.nonProxyHosts = 'localhost'
        def planner = new WagonProxyInfoRoutePlanner(proxyInfo)
        planner
    }

    @Test
    void notDirect() {
        // arrange
        WagonProxyInfoRoutePlanner planner = getPlanner()

        // act
        def result = planner.isDirect('www.google.com')

        // assert
        assertThat result,
                   is(equalTo(false))
    }
}
