plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName = "aimodel"
    dynamicDelivery {
        // fast-follow, not install-time: install-time packs stay compressed
        // *inside* the split APK and Play never extracts them, so
        // AssetPackManager.getPackLocation() returns null and MediaPipe (which
        // needs a real file path, not an asset stream) can only get at the
        // model by extracting a second copy - roughly doubling on-device
        // storage. fast-follow makes Play download and unpack it to disk right
        // after install, so getPackLocation() yields a usable path and only one
        // copy ever exists.
        deliveryType = "fast-follow"
    }
}