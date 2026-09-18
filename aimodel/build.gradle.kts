plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName = "aimodel"
    dynamicDelivery {
        deliveryType = "install-time"
    }
}