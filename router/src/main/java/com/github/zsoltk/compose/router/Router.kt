package com.github.zsoltk.compose.router

import androidx.compose.Ambient
import androidx.compose.Composable
import androidx.compose.ambient
import androidx.compose.onCommit
import androidx.compose.remember
import com.github.zsoltk.compose.backpress.BackPressHandler
import com.github.zsoltk.compose.backpress.backPressHandler
import com.github.zsoltk.compose.savedinstancestate.BundleScope
import com.github.zsoltk.compose.savedinstancestate.savedInstanceState

private fun key(backStackIndex: Int) =
    "K$backStackIndex"

private val backStackMap = Ambient.of<MutableMap<Any, BackStack<*>>> {
    mutableMapOf()
}

/**
 * Currently only used for deep link based Routing.
 *
 * Can be set to store a list of Routing elements of different types.
 * The idea is that when we walk through this list in sequence - provided that the sequence
 * is correct - we can set the app into any state that is a combination of Routing on different levels.
 *
 * See [com.example.lifelike.DeepLinkKt.parseProfileDeepLink] in :app-lifelike module for usage
 * example.
 */
val routing = Ambient.of {
    listOf<Any>()
}

/**
 * Adds back stack functionality with bubbling up fallbacks if the back stack cannot be popped
 * on this level.
 *
 * @param defaultRouting   The default routing to initialise the back stack with
 * @param children  The @Composable to wrap with this BackHandler. It will have access to the back stack.
 */
@Composable
fun <T> Router(contextId: String, defaultRouting: T, children: @Composable() (BackStack<T>) -> Unit) {
    val route = ambient(routing)
    val routingFromAmbient = route.firstOrNull() as? T
    val downStreamRoute = if (route.size > 1) route.takeLast(route.size - 1) else emptyList()

    val upstreamHandler = ambient(backPressHandler)
    val localHandler = remember { BackPressHandler("${upstreamHandler.id}.$contextId") }
    val backStack = fetchBackStack(localHandler.id, defaultRouting, routingFromAmbient)
    val handleBackPressHere: () -> Boolean = { localHandler.handle() || backStack.pop() }

    onCommit {
        upstreamHandler.children.add(handleBackPressHere)
        onDispose { upstreamHandler.children.remove(handleBackPressHere) }
    }

    BundleScope(key(backStack.lastIndex), autoDispose = false) {
        backPressHandler.Provider(value = localHandler) {
            routing.Provider(value = downStreamRoute) {
                children(backStack)
            }
        }
    }
}

private fun <T> fetchBackStack(key: String, defaultElement: T, override: T?): BackStack<T> {
    val upstreamBundle = ambient(savedInstanceState)
    val onElementRemoved: (Int) -> Unit = { upstreamBundle.remove(key(it)) }

    val upstreamBackStacks = ambient(backStackMap)
    val existing = upstreamBackStacks[key] as BackStack<T>?

    return when {
        override != null -> BackStack(override, onElementRemoved)
        existing != null -> existing
        else -> BackStack(defaultElement, onElementRemoved)

    }.also {
        upstreamBackStacks[key] = it
    }
}

