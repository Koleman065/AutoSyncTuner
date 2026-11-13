package com.edk.autosynctuner.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.edk.autosynctuner.data.AutoSyncClient

object Routes {
    const val LOGIN = "login"
    const val VEHICLES = "vehicles"
    const val VEHICLE = "vehicle/{vin}"
    const val SETTING = "setting/{vin}/{id}?name={name}"
    fun vehicle(vin: String) = "vehicle/$vin"
    fun setting(vin: String, id: String, name: String) = "setting/$vin/$id?name=${Uri.encode(name)}"
}

@Composable
fun AppNav(client: AutoSyncClient) {
    val nav = rememberNavController()
    val start = remember { if (client.hasSession) Routes.VEHICLES else Routes.LOGIN }

    fun toLogin(n: NavHostController) = n.navigate(Routes.LOGIN) { popUpTo(0) }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(client) { nav.navigate(Routes.VEHICLES) { popUpTo(0) } }
        }
        composable(Routes.VEHICLES) {
            VehiclesScreen(
                client = client,
                onOpen = { vin, single ->
                    nav.navigate(Routes.vehicle(vin)) { if (single) popUpTo(Routes.VEHICLES) { inclusive = true } }
                },
                onSignedOut = { toLogin(nav) },
            )
        }
        composable(Routes.VEHICLE, arguments = listOf(navArgument("vin") { type = NavType.StringType })) { e ->
            val vin = e.arguments!!.getString("vin")!!
            VehicleScreen(
                client = client,
                vin = vin,
                onOpenSetting = { id, name -> nav.navigate(Routes.setting(vin, id, name)) },
                onSignedOut = { toLogin(nav) },
            )
        }
        composable(
            Routes.SETTING,
            arguments = listOf(
                navArgument("vin") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { e ->
            val a = e.arguments!!
            SettingScreen(
                client = client,
                vin = a.getString("vin")!!,
                id = a.getString("id")!!,
                fallbackTitle = a.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
                onSignedOut = { toLogin(nav) },
            )
        }
    }
}
