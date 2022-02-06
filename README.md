# Yggdrasil Android

Reference implementation for connecting to the Yggdrasil Network from Android devices

# Build

Download this repository and
the [Go Yggdrasil repository](https://github.com/yggdrasil-network/yggdrasil-go).

```
git clone git@github.com:yggdrasil-network/yggdrasil-android.git
git clone git@github.com:yggdrasil-network/yggdrasil-go.git

# or

git clone https://github.com/yggdrasil-network/yggdrasil-android.git
git clone https://github.com/yggdrasil-network/yggdrasil-go.git
```

## Generate the Android artifact (.aar) from the Go repository

Build the Android artifact from the `yggdrasil-go` repo.

```
# from yggdrasil-go
./contrib/mobile/build -a
```

If you check the folder, there should now be a `yggdrasil-sources.jar` and `yggdrasil.aar` there.

## Move the Android artifact to the Android repository

From the Android repo, copy `yggdrasil.aar` into the `app/libs` folder.

```
# from yggdrasil-android
cp ../yggdrasil-go/yggdrasil.aar app/libs/
```

## Build and install the Android app

With your Android device connected or emulator running, build and install the app.

```
# from yggdrasil-android
./gradlew :app:installDebug
```

# Configure

## Peer

Add a peer from the [public peer list](https://publicpeers.neilalexander.dev/). Make sure to change
the "enable" toggle from off to on to make sure the peer(s) get picked up.

## Explore

Try out pages from the [list of services](https://yggdrasil-network.github.io/services.html).
