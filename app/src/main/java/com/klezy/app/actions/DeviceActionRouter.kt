package com.klezy.app.actions

import android.content.Context
import com.klezy.app.accessibility.KlezyAccessibilityService

/**
 * DeviceActionRouter
 *
 * The single entry point the AI/chat layer calls to make something happen
 * on the phone. It decides whether an Intent is enough or whether it needs
 * the Accessibility Service (for tapping inside another app's UI).
 *
 * This is intentionally small for P1 — P2's automation engine will build
 * triggers on top of this, not replace it.
 */
object DeviceActionRouter {

    sealed class ActionResult {
        object Success : ActionResult()
        data class Failed(val reason: String) : ActionResult()
    }

    fun openApp(context: Context, packageName: String): ActionResult =
        if (IntentActionHandler.openApp(context, packageName)) ActionResult.Success
        else ActionResult.Failed("App not installed or package name wrong: $packageName")

    fun sendText(context: Context, number: String, message: String): ActionResult {
        IntentActionHandler.composeSms(context, number, message)
        return ActionResult.Success
    }

    fun call(context: Context, number: String, direct: Boolean = false): ActionResult {
        if (direct) IntentActionHandler.callNumber(context, number)
        else IntentActionHandler.dialNumber(context, number)
        return ActionResult.Success
    }

    /** Taps whatever on-screen element matches [label]. Requires the accessibility service to be on. */
    fun tapOnScreen(label: String): ActionResult {
        val service = KlezyAccessibilityService.instance
            ?: return ActionResult.Failed("Accessibility service not enabled")
        return if (service.tapByText(label)) ActionResult.Success
        else ActionResult.Failed("No visible element matched: $label")
    }

    fun typeText(text: String): ActionResult {
        val service = KlezyAccessibilityService.instance
            ?: return ActionResult.Failed("Accessibility service not enabled")
        return if (service.typeIntoFocusedField(text)) ActionResult.Success
        else ActionResult.Failed("No focused input field found")
    }

    fun readScreen(): String =
        KlezyAccessibilityService.instance?.getScreenText() ?: ""

    fun goBack(): ActionResult {
        val service = KlezyAccessibilityService.instance
            ?: return ActionResult.Failed("Accessibility service not enabled")
        service.goBack()
        return ActionResult.Success
    }

    fun goHome(): ActionResult {
        val service = KlezyAccessibilityService.instance
            ?: return ActionResult.Failed("Accessibility service not enabled")
        service.goHome()
        return ActionResult.Success
    }
}
