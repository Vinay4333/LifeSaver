package app.lifesaver.ui

import app.lifesaver.vpn.VpnPhase

/** What the user sees: one plain state, derived from the VPN phase and whether consent is held. */
enum class ProtectionState {
    Protected,
    Unlocked,
    Starting,
    NeedsConsent,
    Down,
    Error,
}

fun protectionState(
    phase: VpnPhase,
    hasConsent: Boolean,
    unlocked: Boolean = false,
): ProtectionState =
    when (phase) {
        VpnPhase.On -> if (unlocked) ProtectionState.Unlocked else ProtectionState.Protected
        VpnPhase.Starting -> ProtectionState.Starting
        VpnPhase.Error -> ProtectionState.Error
        VpnPhase.Off -> if (hasConsent) ProtectionState.Down else ProtectionState.NeedsConsent
    }
