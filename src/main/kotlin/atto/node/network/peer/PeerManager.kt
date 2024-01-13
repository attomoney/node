package atto.node.network.peer

import atto.node.CacheSupport
import atto.node.EventPublisher
import atto.node.network.DirectNetworkMessage
import atto.node.network.InboundNetworkMessage
import atto.node.network.NetworkMessagePublisher
import atto.node.network.NodeDisconnected
import atto.protocol.network.peer.AttoKeepAlive
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

@Service
class PeerManager(
    properties: PeerProperties,
    val handshakeService: HandshakeService,
    val eventPublisher: EventPublisher,
    val messagePublisher: NetworkMessagePublisher,
) : CacheSupport {
    private val peers = Caffeine.newBuilder()
        .expireAfterWrite(properties.expirationTimeInSeconds, TimeUnit.SECONDS)
        .removalListener { _: InetSocketAddress?, peer: Peer?, _ ->
            peer?.let { eventPublisher.publish(PeerRemoved(it)) }
        }.build<InetSocketAddress, Peer>()
        .asMap()

    @EventListener
    fun process(peerEvent: PeerAdded) {
        val peer = peerEvent.peer
        peers[peer.connectionSocketAddress] = peer
    }

    @EventListener
    fun process(nodeEvent: NodeDisconnected) {
        peers.remove(nodeEvent.connectionSocketAddress)
    }

    @EventListener
    fun process(message: InboundNetworkMessage<AttoKeepAlive>) {
        peers.compute(message.socketAddress) { _, value -> value } // refresh cache

        message.payload.neighbours.forEach { handshakeService.startHandshake(it) }
    }

    @Scheduled(cron = "0/10 * * * * *")
    fun sendKeepAlive() {
        val peerSample = peerSample()

        peers.keys.forEach {
            messagePublisher.publish(DirectNetworkMessage(it, AttoKeepAlive(peerSample)))
        }
    }

    fun getPeers(): List<Peer> {
        return peers.values.toList()
    }

    private fun peerSample(): List<InetSocketAddress> {
        return peers.values.asSequence()
            .map { it.node.socketAddress }
            .shuffled()
            .take(8)
            .toList()
    }

    override fun clear() {
        peers.clear()
    }
}