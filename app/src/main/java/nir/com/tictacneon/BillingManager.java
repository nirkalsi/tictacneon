package nir.com.tictacneon;

import android.content.Context;
import com.android.billingclient.api.*;
import java.util.*;

/** Google Play Billing: remove-ads restore only (existing owners stay ad-free). No purchasable products. */
public class BillingManager implements PurchasesUpdatedListener {
    public static final String REMOVE_ADS = "remove_ads";

    public interface Listener {
        void onAdsRemovedOwned();
    }

    private final Context ctx;
    private final Listener listener;
    private BillingClient bc;
    private boolean ready = false;

    public BillingManager(Context c, Listener l) {
        ctx = c.getApplicationContext(); listener = l;
        bc = BillingClient.newBuilder(ctx)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
        connect();
    }

    private void connect() {
        bc.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    ready = true;
                    restorePurchases();
                }
            }
            @Override public void onBillingServiceDisconnected() { ready = false; }
        });
    }

    private void restorePurchases() {
        bc.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build(), (r, list) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            for (Purchase p : list) handlePurchase(p);
        });
    }


    @Override public void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) handlePurchase(p);
        }
    }

    private void handlePurchase(Purchase p) {
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        for (String id : p.getProducts()) {
            if (REMOVE_ADS.equals(id)) {
                if (!p.isAcknowledged()) {
                    bc.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken()).build(), rr -> {});
                }
                listener.onAdsRemovedOwned();
            }
        }
    }
}
