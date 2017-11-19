# Purpose

1. Reconfigure Maven's HTTP transport to use SPNEGO/Windows friendly authentication for proxies and hosts.
1. Automatically fetches tokens to use when authenticating to the Anypoint Exchange Maven server. 

# Installing

1. Run gradlew clean install. This builds a JAR and installs in your Maven `lib/ext` directory.
2. Run a mvn clean compile -U on any project

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