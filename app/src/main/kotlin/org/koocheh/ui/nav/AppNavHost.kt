package org.koocheh.ui.nav

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.koocheh.chat.ChatService
import org.koocheh.security.SecureWindowPolicy
import org.koocheh.ui.chat.ChatScreen
import org.koocheh.ui.disclosure.DisclosureViewModel
import org.koocheh.ui.disclosure.HelloFlowScreen
import org.koocheh.ui.signals.SignalsScreen
import org.koocheh.ui.signals.SignalsViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOCK = "lock"
    const val PROFILE = "profile"
    const val SIGNALS = "signals"
    const val HELLO = "hello"
    const val CHAT = "chat"
    const val CHAT_PATTERN = "chat/{peerKey}"
    const val SETTINGS = "settings"
}

/** Call from every screen: applies SecureWindowPolicy to the host window. */
@Composable
fun ApplySecureWindow() {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(activity) { activity?.window?.let { SecureWindowPolicy.apply(it) } }
}

private fun <T : ViewModel> factoryOf(create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}

/**
 * Screens built elsewhere (onboarding, lock, profile, settings) are supplied in
 * [externalScreens], keyed by the route constants above. This host only knows their route names.
 */
@Composable
fun AppNavHost(
    startRoute: String,
    externalScreens: Map<String, @Composable (NavHostController) -> Unit>,
    signalsViewModel: () -> SignalsViewModel,
    disclosureViewModel: () -> DisclosureViewModel,
    chatService: ChatService,
    helloPeerKey: () -> String,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startRoute) {
        listOf(Routes.ONBOARDING, Routes.LOCK, Routes.PROFILE, Routes.SETTINGS).forEach { route ->
            composable(route) { externalScreens[route]?.invoke(navController) }
        }
        composable(Routes.SIGNALS) {
            val vm: SignalsViewModel = viewModel(factory = factoryOf(signalsViewModel))
            SignalsScreen(vm, onSayHello = { navController.navigate(Routes.HELLO) })
        }
        composable(Routes.HELLO) {
            val vm: DisclosureViewModel = viewModel(factory = factoryOf(disclosureViewModel))
            HelloFlowScreen(
                vm,
                onWithdrawn = { navController.popBackStack(Routes.SIGNALS, inclusive = false) },
                onOpenChat = { navController.navigate("${Routes.CHAT}/${helloPeerKey()}") },
            )
        }
        composable(
            Routes.CHAT_PATTERN,
            arguments = listOf(navArgument("peerKey") { type = NavType.StringType }),
        ) { entry ->
            ChatScreen(chatService, entry.arguments?.getString("peerKey").orEmpty())
        }
    }
}
