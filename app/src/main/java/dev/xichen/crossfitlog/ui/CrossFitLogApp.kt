package dev.xichen.crossfitlog.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.xichen.crossfitlog.CrossFitLogApplication

@Composable
fun CrossFitLogApp(app: CrossFitLogApplication) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "sessions") {
        composable("sessions") {
            val vm: SessionsViewModel = viewModel(factory = viewModelFactory { initializer { SessionsViewModel(app.repository) } })
            SessionListScreen(vm.sessions, app.photoStore, { nav.navigate("editor/new") }, { nav.navigate("details/$it") }, { nav.navigate("history") }, { nav.navigate("settings") })
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val rawId = entry.arguments?.getString("id") ?: "new"
            val existingId = rawId.takeUnless { it == "new" }
            val vm: EditorViewModel = viewModel(key = "editor-$rawId", factory = viewModelFactory {
                initializer { EditorViewModel(createSavedStateHandle(), app.repository, app.photoStore, app.whiteboardTextRecognizer, existingId) }
            })
            EditorScreen(vm, app.photoStore, onBack = { nav.popBackStack() }, onSaved = {
                nav.navigate("details/${vm.state.value.draft.id}") { popUpTo("sessions") }
            }, onDeleted = { nav.navigate("sessions") { popUpTo("sessions") { inclusive = true } } })
        }
        composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm: DetailViewModel = viewModel(key = "detail-$id", factory = viewModelFactory { initializer { DetailViewModel(app.repository, id) } })
            DetailScreen(vm.session, app.photoStore, { nav.popBackStack() }, { nav.navigate("editor/$id") })
        }
        composable("history") {
            val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(app.repository) } })
            HistoryScreen(vm, app.photoStore, { nav.popBackStack() }, { nav.navigate("details/$it") })
        }
        composable("settings") { SettingsScreen(app.backupService, app.dataExportService, { nav.popBackStack() }) }
    }
}
