Yggdrasil Android
-----------------

Yggdrasil is an early-stage implementation of a fully end-to-end encrypted IPv6 network. It is lightweight, self-arranging, supported on multiple platforms and allows pretty much any IPv6-capable application to communicate securely with other Yggdrasil nodes. Yggdrasil does not require you to have IPv6 Internet connectivity - it also works over IPv4.

This app allows you to connect to Yggdrasil Network and use any service located in this network. It works as VPN service, but all your usual traffic will go trough your provider, not through Yggdrasil Network.

Also, it is not a goal of the Yggdrasil project to provide anonymity. Direct peers over the Internet will be able to see your IP address and may be able to use this information to determine your location or identity. Multicast-discovered peerings on the same network will typically expose your device MAC address. Other nodes on the network may be able to discern some information about which nodes you are peered with.

All traffic sent across the Yggdrasil network is encrypted end-to-end. Assuming that our crypto is solid, it cannot be decrypted or read by any intermediate nodes, and can only be decrypted by the recipient for which it was intended. However, please note that Yggdrasil has not been officially externally audited. 

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/eu.neilalexander.yggdrasil/)

Or get the APK from the [Releases Section](https://github.com/yggdrasil-network/yggdrasil-android/releases/latest).

## Build Instructions

* install gomobile

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
```

* build yggdrasil-go for android:

```
git clone https://github.com/yggdrasil-network/yggdrasil-go /tmp/yggdrasil-go
cd /tmp/yggdrasil-go
./contrib/mobile/build -a
```

* clone yggdrasil for android and copy over the built go library

```
git clone https://github.com/yggdrasil-network/yggdrasil-android /tmp/yggdrasil-android
mkdir /tmp/yggdrasil-android/app/libs
cp /tmp/yggdrasil-go/yggdrasil.aar /tmp/yggdrasil-android/app/libs/
```

* build yggdrasil-android

```
cd /tmp/yggdrasil-android
./gradlew assembleRelease
```

note: you will need to use jdk-11 as jdk-16 `"doesn't work" ™`

on debian/ubuntu you can set which jdk used with the `JAVA_HOME` env var:
```
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
```
