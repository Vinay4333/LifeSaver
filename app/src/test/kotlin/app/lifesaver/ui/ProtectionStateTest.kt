package app.lifesaver.ui

import app.lifesaver.vpn.VpnPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionStateTest {
    @Test
    fun onIsProtectedWhateverConsentSays() {
        assertEquals(ProtectionState.Protected, protectionState(VpnPhase.On, hasConsent = true))
        assertEquals(ProtectionState.Protected, protectionState(VpnPhase.On, hasConsent = false))
    }

    @Test
    fun offWithConsentIsDown() {
        assertEquals(ProtectionState.Down, protectionState(VpnPhase.Off, hasConsent = true))
    }

    @Test
    fun offWithoutConsentWaitsForConsent() {
        assertEquals(ProtectionState.NeedsConsent, protectionState(VpnPhase.Off, hasConsent = false))
    }

    @Test
    fun startingAndErrorMapDirectly() {
        assertEquals(ProtectionState.Starting, protectionState(VpnPhase.Starting, hasConsent = true))
        assertEquals(ProtectionState.Error, protectionState(VpnPhase.Error, hasConsent = false))
    }
}
