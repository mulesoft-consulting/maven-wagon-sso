# Purpose

1. Reconfigure Maven's HTTP transport to use SPNEGO/Windows friendly authentication for proxies and hosts.
1. Automatically fetches tokens to use when authenticating to the Anypoint Exchange Maven server. 

# Installing

1. Run gradlew clean install. This builds a JAR and installs in your Maven `lib/ext` directory.
2. Run a mvn clean compile -U on any project

# Process

1. Wagon behaves as traditional Maven would until a server is encountered with the `samlIdpUrl` config setting.
1. At that point, the first time it's challenged for authentication by the server (which is usually when fetching the remote artifact's POM, `maven-metadata.xml` is not restricted), it will get an access token by mimicing a user's browser flow and then authenticate as described at [the Exchange publishd doc](https://docs.mulesoft.com/anypoint-exchange/to-publish-assets-maven#to-publish-federated-assets)

# Using

The SPNego part will just work

To configure an Anypoint maven server, put something like this in settings.xml:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <!-- You'll probably need a proxy anyways -->
    <proxies>
        <proxy>
            <id>the-proxy</id>
            <host>proxy.corporate.net</host>
            <active>true</active>
            <port>8080</port>
            <protocol>http</protocol>
            <nonProxyHosts>localhost|*.corporate.net</nonProxyHosts>
        </proxy>
    </proxies>
    <servers>
        <server>
            <id>mulesoft-exchange</id>
            <configuration>
                <samlIdpUrl>https//adfs.corporate.net/idp/flow/that/logs/into/Anypoint</samlIdpUrl>
            </configuration>
        </server>
    </servers>
</settings>
```

# Design

1. Used Groovy to code parts of this since it's SOOO much less verbose

# TODOs

1. Instead of requiring the full IDP url, could just take in the org ID like Studio and configure an AMC URL that will do the IDP redirect.
1. The build will bundle lots of JAR dependencies into the finished product, which is quite large (20MB). There are probably classes in the JAR that aren't used. If someone had the time and the shadow/jar plugins for Gradle can aid in this, might be nice to only bundled classes that are used (and not already present with Maven)
