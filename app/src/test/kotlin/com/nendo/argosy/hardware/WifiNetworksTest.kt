package com.nendo.argosy.hardware

import com.nendo.argosy.util.RootShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworksTest {

    private val statusFixture = listOf(
        "Wifi is enabled",
        "Wifi scanning is always available",
        "==== Primary ClientModeManager instance ====",
        "Wifi is connected to \"perseus\"",
        "WifiInfo: SSID: \"perseus\", BSSID: aa:bb:cc:dd:ee:01, MAC: 02:00:00:00:00:00, " +
            "Security type: 2, Supplicant state: COMPLETED, Wi-Fi standard: 5, RSSI: -70, " +
            "Link speed: 433Mbps, Tx Link speed: 433Mbps, Frequency: 5220MHz, Net ID: 0"
    )

    private val savedFixture = listOf(
        "Network Id      SSID                         Security type",
        "0            perseus                          wpa2-psk",
        "0            perseus                          wpa3-sae^",
        "1            Coffee Shop Guest                open"
    )

    private val scanFixture = listOf(
        "    BSSID              Frequency      RSSI           Age(sec)     SSID                                 Flags",
        "  aa:bb:cc:dd:ee:01       5220     -70    0.512    perseus                           [WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
        "  aa:bb:cc:dd:ee:02       2437     -58    1.204    perseus                           [WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
        "  aa:bb:cc:dd:ee:03       2412     -81    0.900    Coffee Shop Guest                 [ESS]",
        "  aa:bb:cc:dd:ee:04       5745     -66    0.300    Neighbor 6E                       [RSN-SAE-CCMP][ESS]",
        "  aa:bb:cc:dd:ee:05       2462     -75    2.010    Office                            [WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]",
        "  aa:bb:cc:dd:ee:06       2412     -90    3.000                                      [WPA2-PSK-CCMP][ESS]"
    )

    private val androidThirteenScanRows = listOf(
        "    BSSID              Frequency      RSSI           Age(sec)     SSID                                 Flags",
        "  44:38:8c:02:86:32       2437    -73(0:-77/1:-75)    41.028    eoGW-2fd4ca0-5                    [WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]",
        "  f8:94:97:a5:7f:48       5680    -76(0:-76/1:-90)    37.120    Buffalo-5G-7F38                   [WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]",
        "  f8:94:97:a5:7f:47       2422    -66(0:-75/1:-67)    41.155    Buffalo-7F38-WPA3                 [RSN-SAE-CCMP][ESS]"
    )

    @Test
    fun `scan rows carrying per-chain signal detail parse`() {
        assertEquals(
            listOf(
                WifiScanResult("Buffalo-7F38-WPA3", rssi = -66, frequencyMhz = 2422, security = WifiSecurity.WPA3),
                WifiScanResult("eoGW-2fd4ca0-5", rssi = -73, frequencyMhz = 2437, security = WifiSecurity.WPA2),
                WifiScanResult("Buffalo-5G-7F38", rssi = -76, frequencyMhz = 5680, security = WifiSecurity.WPA2)
            ),
            WifiShellParser.parseScanResults(androidThirteenScanRows)
        )
    }

    private class RecordingRunner(private val result: RootShell.Result?) : RootScriptRunner {
        val scripts = mutableListOf<String>()

        override fun run(script: String): RootShell.Result? {
            scripts += script
            return result
        }
    }

    @Test
    fun `status yields the connected network, signal and frequency`() {
        assertEquals(WifiConnection("perseus", rssi = -70, frequencyMhz = 5220), WifiShellParser.parseStatus(statusFixture))
    }

    @Test
    fun `status without a connection yields nothing`() {
        assertNull(WifiShellParser.parseStatus(listOf("Wifi is enabled", "Wifi is not connected")))
    }

    @Test
    fun `saved networks keep ids and names with spaces and skip the header`() {
        assertEquals(
            listOf(
                SavedWifiNetwork(0, "perseus", "wpa2-psk"),
                SavedWifiNetwork(1, "Coffee Shop Guest", "open")
            ),
            WifiShellParser.parseSavedNetworks(savedFixture)
        )
    }

    @Test
    fun `scan results keep the strongest sighting per name, sorted by signal, hidden names dropped`() {
        assertEquals(
            listOf(
                WifiScanResult("perseus", rssi = -58, frequencyMhz = 2437, security = WifiSecurity.WPA2),
                WifiScanResult("Neighbor 6E", rssi = -66, frequencyMhz = 5745, security = WifiSecurity.WPA3),
                WifiScanResult("Office", rssi = -75, frequencyMhz = 2462, security = null),
                WifiScanResult("Coffee Shop Guest", rssi = -81, frequencyMhz = 2412, security = WifiSecurity.OPEN)
            ),
            WifiShellParser.parseScanResults(scanFixture)
        )
    }

    @Test
    fun `security flags map to suggestion types`() {
        assertEquals(WifiSecurity.WPA2, WifiShellParser.securityOf("[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA3, WifiShellParser.securityOf("[RSN-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.OWE, WifiShellParser.securityOf("[RSN-OWE-CCMP][ESS]"))
        assertEquals(WifiSecurity.OPEN, WifiShellParser.securityOf("[ESS]"))
        assertNull(WifiShellParser.securityOf("[WEP][ESS]"))
        assertNull(WifiShellParser.securityOf("[RSN-EAP-CCMP][ESS]"))
    }

    @Test
    fun `frequency maps to band`() {
        assertEquals(WifiBand.GHZ_2_4, WifiShellParser.bandOf(2437))
        assertEquals(WifiBand.GHZ_5, WifiShellParser.bandOf(5220))
        assertEquals(WifiBand.GHZ_6, WifiShellParser.bandOf(5955))
        assertNull(WifiShellParser.bandOf(null))
    }

    @Test
    fun `signal level spans zero to four`() {
        assertEquals(4, WifiShellParser.signalLevel(-50))
        assertEquals(2, WifiShellParser.signalLevel(-70))
        assertEquals(0, WifiShellParser.signalLevel(-95))
        assertEquals(0, WifiShellParser.signalLevel(null))
    }

    @Test
    fun `joining a secured network quotes name and passphrase`() {
        val runner = RecordingRunner(RootShell.Result(0, emptyList()))

        assertTrue(WifiNetworks(runner).join("Bob's Wi-Fi", WifiSecurity.WPA2, "pa ss'word"))
        assertEquals(
            listOf("cmd wifi add-suggestion 'Bob'\\''s Wi-Fi' wpa2 'pa ss'\\''word'"),
            runner.scripts
        )
    }

    @Test
    fun `joining an open network sends no passphrase`() {
        val runner = RecordingRunner(RootShell.Result(0, emptyList()))

        WifiNetworks(runner).join("cafe", WifiSecurity.OPEN, "ignored")

        assertEquals(listOf("cmd wifi add-suggestion 'cafe' open"), runner.scripts)
    }

    @Test
    fun `forgetting a network sends its id`() {
        val runner = RecordingRunner(RootShell.Result(0, emptyList()))

        assertTrue(WifiNetworks(runner).forget(3))
        assertEquals(listOf("cmd wifi forget-network 3"), runner.scripts)
    }

    @Test
    fun `a refused or failing shell call reports failure`() {
        assertFalse(WifiNetworks(RecordingRunner(null)).join("cafe", WifiSecurity.OPEN, null))
        assertFalse(WifiNetworks(RecordingRunner(RootShell.Result(1, emptyList()))).forget(3))
    }
}
