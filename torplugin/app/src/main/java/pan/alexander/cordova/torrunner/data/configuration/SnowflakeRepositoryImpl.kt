/*
    This file is part of Cordova Plugin Tor Runner.

    Cordova Plugin Tor Runner is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Cordova Plugin Tor Runner is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Cordova Plugin Tor Runner.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.cordova.torrunner.data.configuration

import pan.alexander.cordova.torrunner.domain.configuration.RendezvousType
import pan.alexander.cordova.torrunner.domain.configuration.SnowflakeRepository
import javax.inject.Inject

private const val SOCKS_ARGUMENT_MAX_LENGTH = 510

class SnowflakeRepositoryImpl @Inject constructor(): SnowflakeRepository {

    override fun getBridgeLines(rendezvousType: RendezvousType): List<String> {
        val bridges = mutableListOf<String>()

        for (base in getBases()) {
            val bridgeWithoutIce = when(rendezvousType) {
                RendezvousType.AMP_CACHE, RendezvousType.CDN77 -> "$base url=${getUrl(rendezvousType)} fronts=${getFronts(rendezvousType).joinToString(",")} utls-imitate=${getUtlsClientID()}"
                RendezvousType.AMAZON_SQS -> "$base sqsqueue=${getUrl(rendezvousType)} sqscreds=${getFronts(rendezvousType).joinToString("")} utls-imitate=${getUtlsClientID()}"
            }

            val stuns = getSnowflakeStunServers().map {
                "stun:$it"
            }

            val bridge = StringBuilder("snowflake $bridgeWithoutIce ice=")
            var counter = 0
            do {
                bridge.append(stuns[counter]).append(",")
                counter++
            } while (counter < stuns.size && bridge.length + stuns[counter].length < SOCKS_ARGUMENT_MAX_LENGTH)

            bridges.add(bridge.removeSuffix(",").toString())
        }

        return bridges
    }

    override fun getBases(): List<String> = listOf(
        "192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72",
        "192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA"
    )

    override fun getUrl(rendezvousType: RendezvousType): String =
        when(rendezvousType) {
            RendezvousType.AMP_CACHE -> "https://snowflake-broker.torproject.net/ ampcache=https://cdn.ampproject.org/"
            RendezvousType.AMAZON_SQS -> "https://sqs.us-east-1.amazonaws.com/${getAmazonId()}/snowflake-broker"
            RendezvousType.CDN77 -> "https://1098762253.rsc.cdn77.org/"
        }

    private fun getAmazonId() = "893902434899"

    override fun getFronts(rendezvousType: RendezvousType): List<String> =
        when(rendezvousType) {
            RendezvousType.AMP_CACHE -> listOf("www.google.com", "cdn.ampproject.org")
            RendezvousType.AMAZON_SQS -> listOf("eyJhd3MtYWNjZXNzLWtleS1pZCI6IkFL", "SUE1QUlGNFdKSlhTN1lIRUczIiwiYXdzLXNlY3", "JldC1rZXkiOiI3U0RNc0pB", "NHM1RitXZWJ1L3pMOHZrMFFXV0lsa1c2Y1dOZlVsQ0tRIn0=")
            RendezvousType.CDN77 -> listOf("docs.plesk.com", "maxst.icons8.com", "app.datapacket.com")
        }

    override fun getSnowflakeStunServers(): List<String> = listOf(
        "stun.nextcloud.com:443",
        "stun.sipgate.net:10000",
        "stun.epygi.com:3478",
        "stun.uls.co.za:3478",
        "stun.voipgate.com:3478",
        "stun.bethesda.net:3478",
        "stun.mixvoip.com:3478",
        "stun.voipia.net:3478",
        "stun.antisip.com:3478"
    )

    override fun getUtlsClientID(): String = "hellorandomizedalpn"
}
