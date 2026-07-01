package com.lostf1sh.pixelplayeross.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Explore.route,
    Screen.Library.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Explore.route -> 1
    Screen.Library.route -> 2
    else -> null
}
