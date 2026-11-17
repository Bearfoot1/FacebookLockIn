package app.lockin.android

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing for the Lock-In Pro subscription.
 *
 * The product id below must exist in the Play Console as a subscription (price set
 * there: $2.99 or $3.00/month). Billing only works for builds installed from Play
 * (an internal testing track is enough); a sideloaded build reports Play as
 * unavailable and Pro stays as last stored.
 *
 * Pro is stored in LockPrefs.pro. It is set true on a purchased subscription and set
 * false only when Play answers OK with no active subscription, so being offline
 * never takes Pro away.
 */
class BillingManager(context: Context, private val onChanged: () -> Unit) {

    private val prefs = LockPrefs(context)
    private var productDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, fullList = false)
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Price text from Play ("$2.99"), or null until Play has answered. */
    val price: String?
        get() = productDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                    else Log.d(TAG, "billing unavailable: ${result.responseCode}")
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        } catch (e: RuntimeException) {
            Log.d(TAG, "billing failed to start", e)
        }
    }

    fun disconnect() {
        try {
            client.endConnection()
        } catch (e: RuntimeException) {
            // Already closed.
        }
    }

    /** Loads the product (for its price) and re-checks the owner's subscriptions. */
    private fun refresh() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        ) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = list.firstOrNull()
                onChanged()
            }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, fullList = true)
        }
    }

    /** Opens Play's purchase sheet. Returns false if the product is not loaded (Play unavailable). */
    fun buy(activity: Activity): Boolean {
        val details = productDetails ?: return false
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun handlePurchases(purchases: List<Purchase>, fullList: Boolean) {
        val active = purchases.filter {
            PRO_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        for (purchase in active) {
            if (!purchase.isAcknowledged) {
                // Unacknowledged purchases are refunded by Play after three days.
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                ) { }
            }
        }
        val before = prefs.pro
        when {
            active.isNotEmpty() -> prefs.pro = true
            // Only a complete, successful list may take Pro away.
            fullList && !prefs.devPro -> prefs.pro = false
        }
        if (prefs.pro != before) onChanged()
    }

    companion object {
        const val PRO_PRODUCT_ID = "lockin_pro_monthly"
        private const val TAG = "LockIn"
    }
}
