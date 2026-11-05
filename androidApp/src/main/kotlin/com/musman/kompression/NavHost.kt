package com.musman.kompression

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.musman.kompression.batch.BatchCompressScreen
import com.musman.kompression.single.SingleCompressScreen


@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CompressRoute.Home
    ) {

        composable<CompressRoute.Home> {
            HomeScreen(navController = navController)
        }

        composable<CompressRoute.Single> {
            SingleCompressScreen()
        }

        composable<CompressRoute.Batch> {
            BatchCompressScreen()
        }
    }
}