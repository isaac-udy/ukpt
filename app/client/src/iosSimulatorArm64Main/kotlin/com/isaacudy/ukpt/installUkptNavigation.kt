package com.isaacudy.ukpt

import platform.UIKit.UIApplication

internal actual fun installUkptNavigation() {
    UkptNavigation.installNavigationController(UIApplication.sharedApplication())
}
