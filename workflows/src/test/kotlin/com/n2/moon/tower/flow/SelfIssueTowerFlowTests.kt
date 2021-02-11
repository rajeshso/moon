package com.n2.moon.tower.flow


import com.n2.moon.tower.flows.SelfIssueTowerFlow
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.jupiter.api.Test
import java.util.*

class SelfIssueTowerFlowTests {
    private val network = MockNetwork(
            MockNetworkParameters(
                    cordappsForAllNodes = listOf(
                            TestCordapp.findCordapp("com.n2.moon.tower")
                    )
            )
    )
    private val towerInfrastructureProviderNode = network.createNode(CordaX500Name("Arqiva", "", "GB"))


    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `self issue 1 Tower`() {
        val selfIssueTowerFlow = SelfIssueTowerFlow("51.507351", "-0.127758", "1", "Ericsson Monopole 5234", 12, Amount.zero(Currency.getInstance("GBP")), towerInfrastructureProviderNode.info.identityFromX500Name(CordaX500Name("Arqiva", "", "GB")))
        val cordaFuture = this.towerInfrastructureProviderNode.startFlow(selfIssueTowerFlow)
        network.runNetwork()
        val result = cordaFuture.toCompletableFuture().get()
        assertThat(result.latitude).isEqualTo("51.507351")
    }

}