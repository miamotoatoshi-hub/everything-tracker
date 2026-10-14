package com.example.trackly

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("probe") }
    }
}

@Suppress("unused")
object BillingApiProbe {
    fun client(context: Context): BillingClient =
        BillingClient.newBuilder(context)
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

    suspend fun run(client: BillingClient, activity: Activity) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("x")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            ).build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()
        val offer = details?.subscriptionOfferDetails?.firstOrNull()
        val phase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
        println("${phase?.formattedPrice} ${phase?.priceAmountMicros} ${phase?.priceCurrencyCode} ${phase?.billingPeriod}")
        if (details != null && offer != null) {
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .setOfferToken(offer.offerToken)
                            .build(),
                    ),
                ).build()
            client.launchBillingFlow(activity, flow)
        }
        val purchases = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        purchases.purchasesList.forEach { p: Purchase ->
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
                )
            }
            println("${p.products} ${p.isAutoRenewing}")
        }
    }
}
