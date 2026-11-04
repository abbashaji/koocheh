package org.koocheh

import android.content.Context
import org.koocheh.model.ScanMode

/** Raised when a dependency is requested before the integration cell wires it. */
class NotWiredYet(name: String) :
    IllegalStateException("$name is not wired yet; the integration cell must provide it")

// Interfaces only; real implementations come from other modules.
interface DiscoveryService {
    fun start(mode: ScanMode)
    fun stop()
}

interface DisclosureEngine
interface FamiliarTracker
interface ChatService
interface KeyVault

interface PanicWipe {
    fun wipe()
}

object ServiceLocator {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context = appContext ?: throw NotWiredYet("Context")

    // The integration cell assigns these factories before first use.
    var discoveryFactory: (() -> DiscoveryService)? = null
    var disclosureFactory: (() -> DisclosureEngine)? = null
    var familiarFactory: (() -> FamiliarTracker)? = null
    var chatFactory: (() -> ChatService)? = null
    var keyVaultFactory: (() -> KeyVault)? = null
    var panicWipeFactory: (() -> PanicWipe)? = null

    val discoveryService: DiscoveryService by lazy {
        (discoveryFactory ?: throw NotWiredYet("DiscoveryService"))()
    }
    val disclosureEngine: DisclosureEngine by lazy {
        (disclosureFactory ?: throw NotWiredYet("DisclosureEngine"))()
    }
    val familiarTracker: FamiliarTracker by lazy {
        (familiarFactory ?: throw NotWiredYet("FamiliarTracker"))()
    }
    val chatService: ChatService by lazy {
        (chatFactory ?: throw NotWiredYet("ChatService"))()
    }
    val keyVault: KeyVault by lazy {
        (keyVaultFactory ?: throw NotWiredYet("KeyVault"))()
    }
    val panicWipe: PanicWipe by lazy {
        (panicWipeFactory ?: throw NotWiredYet("PanicWipe"))()
    }
}
