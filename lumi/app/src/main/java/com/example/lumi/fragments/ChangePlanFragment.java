package com.example.lumi.fragments;

import android.content.Context;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumi.R;
import com.example.lumi.adapters.PlanAdapter;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.models.Plan;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ChangePlanFragment extends Fragment {

    private AppCompatActivity parent;

    private RecyclerView plansRecycler;
    private ProgressBar loadingPlans;
    private TextView selectedPlanText;
    private TextView plansErrorText;
    private Button changeSubscriptionButton;
    private PlanAdapter adapter;
    private String activePlanId;
    private String selectedPlanId;
    private SessionManager sessionManager;
    private final List<Plan> loadedPlans = new ArrayList<>();
    private PaymentSheet paymentSheet;
    private Plan pendingPlanChange;
    private String pendingClientSecret;

    private FirebaseAuth mAuth;

    // Replace this with your Stripe test publishable key.
    private static final String STRIPE_PUBLISHABLE_KEY = "pk_test_51TBATFJDQsecEfaDE0dK3VEfAc7cYnY2EijRRezu6qbrNxQF6oRbGMPSZoZIrmKPRvvPBNfDiqY3B4P9ciOpP9Eo00RIQYFCRk";
    private static final String STRIPE_MERCHANT_DISPLAY_NAME = "Lumi";
    private static final String STRIPE_INTENT_ENDPOINT = "/payments/create-intent";

    public ChangePlanFragment() {
        // Required empty public constructor for Fragment recreation.
    }

    public ChangePlanFragment(AppCompatActivity parent) {
        this.parent = parent;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            parent = (AppCompatActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout header = new LinearLayout(requireContext());
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton backButton = new ImageButton(requireContext());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        backButton.setLayoutParams(backLp);
        backButton.setBackgroundColor(Color.TRANSPARENT);
        backButton.setImageResource(android.R.drawable.ic_media_previous);
        backButton.setContentDescription("Back");

        TextView title = new TextView(requireContext());
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.leftMargin = dp(8);
        title.setLayoutParams(titleLp);
        title.setText("Change Plan");
        title.setTextSize(22f);
        title.setTextColor(Color.BLACK);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);

        selectedPlanText = new TextView(requireContext());
        LinearLayout.LayoutParams selectedLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        selectedLp.topMargin = dp(16);
        selectedPlanText.setLayoutParams(selectedLp);
        selectedPlanText.setText("Selected plan: none");
        selectedPlanText.setTextColor(getResources().getColor(R.color.gray_600, null));
        selectedPlanText.setTextSize(14f);

        loadingPlans = new ProgressBar(requireContext());
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        loadingLp.topMargin = dp(20);
        loadingPlans.setLayoutParams(loadingLp);

        plansErrorText = new TextView(requireContext());
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        errorLp.topMargin = dp(20);
        plansErrorText.setLayoutParams(errorLp);
        plansErrorText.setText("Failed to load plans");
        plansErrorText.setGravity(Gravity.CENTER_HORIZONTAL);
        plansErrorText.setTextColor(getResources().getColor(R.color.toast_error, null));
        plansErrorText.setVisibility(View.GONE);

        changeSubscriptionButton = new Button(requireContext());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonLp.topMargin = dp(12);
        changeSubscriptionButton.setLayoutParams(buttonLp);
        changeSubscriptionButton.setText("Change Subscription");
        changeSubscriptionButton.setEnabled(false);

        plansRecycler = new RecyclerView(requireContext());
        LinearLayout.LayoutParams recyclerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
        );
        recyclerLp.topMargin = dp(12);
        recyclerLp.weight = 1f;
        plansRecycler.setLayoutParams(recyclerLp);
        plansRecycler.setVisibility(View.GONE);

        header.addView(backButton);
        header.addView(title);
        root.addView(header);
        root.addView(selectedPlanText);
        root.addView(loadingPlans);
        root.addView(plansErrorText);
        root.addView(changeSubscriptionButton);
        root.addView(plansRecycler);

        plansRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PlanAdapter(requireContext(), plan -> {
            selectedPlanId = plan.getId();
            selectedPlanText.setText("Selected plan: " + plan.getName());
            updateChangeButtonState();
        });
        plansRecycler.setAdapter(adapter);

        backButton.setOnClickListener(v -> parent.getSupportFragmentManager().popBackStack());
        changeSubscriptionButton.setOnClickListener(v -> onChangeSubscriptionClicked());

//        init firebase auth
        mAuth = FirebaseAuth.getInstance();

        PaymentConfiguration.init(requireContext(), STRIPE_PUBLISHABLE_KEY);
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);

        loadPlans();
        return root;
    }

    private void loadPlans() {
        loadingPlans.setVisibility(View.VISIBLE);
        plansRecycler.setVisibility(View.GONE);
        plansErrorText.setVisibility(View.GONE);

        if (mAuth.getCurrentUser() == null) {
            loadingPlans.setVisibility(View.GONE);
            plansRecycler.setVisibility(View.GONE);
            plansErrorText.setVisibility(View.VISIBLE);
            return;
        }

        mAuth.getCurrentUser().getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().getToken() == null) {
                if (!isAdded()) {
                    return;
                }
                parent.runOnUiThread(() -> {
                    loadingPlans.setVisibility(View.GONE);
                    plansRecycler.setVisibility(View.GONE);
                    plansErrorText.setVisibility(View.VISIBLE);
                    selectedPlanText.setText("Selected plan: none");
                    updateChangeButtonState();
                });
                return;
            }

            String authToken = task.getResult().getToken();
            String firebaseUid = mAuth.getCurrentUser().getUid();

            new Thread(() -> {
                try {
                    JsonElement plansResponse = API.GET("/plans", authToken);
                    List<Plan> plans = parsePlans(plansResponse);
                    String currentPlanFromApi = fetchCurrentSubscriptionPlanId(authToken, firebaseUid);

                    if (!isAdded()) {
                        return;
                    }

                    parent.runOnUiThread(() -> {
                        loadingPlans.setVisibility(View.GONE);
                        plansRecycler.setVisibility(View.VISIBLE);
                        adapter.submitPlans(plans);
                        loadedPlans.clear();
                        loadedPlans.addAll(plans);

                        Plan initialPlan = pickInitialPlan(plans, currentPlanFromApi);
                        if (initialPlan != null) {
                            activePlanId = initialPlan.getId();
                            selectedPlanId = initialPlan.getId();
                            adapter.setActivePlanId(activePlanId);
                            adapter.setSelectedPlanId(selectedPlanId);
                            selectedPlanText.setText("Selected plan: " + initialPlan.getName());
                        } else {
                            activePlanId = null;
                            selectedPlanId = null;
                            selectedPlanText.setText("Selected plan: none");
                        }
                        updateChangeButtonState();
                    });
                } catch (Exception e) {
                    if (!isAdded()) {
                        return;
                    }
                    parent.runOnUiThread(() -> {
                        loadingPlans.setVisibility(View.GONE);
                        plansRecycler.setVisibility(View.GONE);
                        plansErrorText.setVisibility(View.VISIBLE);
                        activePlanId = null;
                        selectedPlanId = null;
                        selectedPlanText.setText("Selected plan: none");
                        updateChangeButtonState();
                    });
                }
            }).start();
        });
    }

    private void onChangeSubscriptionClicked() {
        Plan selectedPlan = findPlanById(selectedPlanId);
        if (selectedPlan == null) {
            return;
        }

        // Free plan can be activated without payment intent.
        if (selectedPlan.getPrice() <= 0d) {
            sendSubscriptionUpdate(selectedPlan, "FREE_PLAN");
            return;
        }

        startStripeSandboxPayment(selectedPlan);
    }

    private void startStripeSandboxPayment(Plan selectedPlan) {
        if (mAuth.getCurrentUser() == null) {
            android.widget.Toast.makeText(requireContext(), "Please sign in again", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String firebaseUid = mAuth.getCurrentUser().getUid();
        pendingPlanChange = selectedPlan;
        changeSubscriptionButton.setEnabled(false);
        changeSubscriptionButton.setAlpha(0.5f);

        mAuth.getCurrentUser().getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().getToken() == null) {
                updateChangeButtonState();
                android.widget.Toast.makeText(requireContext(), "Please sign in again", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            String authToken = task.getResult().getToken();
            new Thread(() -> {
                try {
                    String clientSecret = createPaymentIntentClientSecret(selectedPlan, authToken, firebaseUid);
                    if (!isAdded()) {
                        return;
                    }

                    if (clientSecret == null || clientSecret.trim().isEmpty()) {
                        parent.runOnUiThread(() -> {
                            updateChangeButtonState();
                            android.widget.Toast.makeText(requireContext(), "Unable to start payment", android.widget.Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    pendingClientSecret = clientSecret;
                    parent.runOnUiThread(() -> paymentSheet.presentWithPaymentIntent(
                            clientSecret,
                            new PaymentSheet.Configuration(STRIPE_MERCHANT_DISPLAY_NAME)
                    ));
                } catch (Exception e) {
                    if (!isAdded()) {
                        return;
                    }
                    parent.runOnUiThread(() -> {
                        updateChangeButtonState();
                        android.widget.Toast.makeText(requireContext(), "Payment init failed", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
    }

    private String createPaymentIntentClientSecret(Plan plan, String authToken, String firebaseUid) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("amount", toMinorUnits(plan.getPrice()));
        req.addProperty("currency", "usd");
        req.addProperty("planId", plan.getId());
        req.addProperty("firebaseUserId", firebaseUid);

        JsonObject response = API.POST(STRIPE_INTENT_ENDPOINT, authToken, req);
        if (response == null) {
            return null;
        }

        if (response.has("clientSecret") && !response.get("clientSecret").isJsonNull()) {
            return response.get("clientSecret").getAsString();
        }
        if (response.has("paymentIntentClientSecret") && !response.get("paymentIntentClientSecret").isJsonNull()) {
            return response.get("paymentIntentClientSecret").getAsString();
        }
        return null;
    }

    private void onPaymentSheetResult(PaymentSheetResult result) {
        if (result instanceof PaymentSheetResult.Completed) {
            if (pendingPlanChange == null) {
                updateChangeButtonState();
                return;
            }
            String merchantRef = extractPaymentIntentId(pendingClientSecret);
            sendSubscriptionUpdate(pendingPlanChange, merchantRef == null ? "UNKNOWN_PAYMENT" : merchantRef);
            return;
        }

        updateChangeButtonState();
        if (result instanceof PaymentSheetResult.Canceled) {
            android.widget.Toast.makeText(requireContext(), "Payment canceled", android.widget.Toast.LENGTH_SHORT).show();
        } else if (result instanceof PaymentSheetResult.Failed) {
            android.widget.Toast.makeText(requireContext(), "Payment failed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void sendSubscriptionUpdate(Plan plan, String stripeMerchantId) {
        if (mAuth.getCurrentUser() == null) {
            android.widget.Toast.makeText(requireContext(), "Please sign in again", android.widget.Toast.LENGTH_SHORT).show();
            updateChangeButtonState();
            return;
        }

        changeSubscriptionButton.setEnabled(false);
        changeSubscriptionButton.setAlpha(0.5f);

        String endpoint = "/subscription";
        JsonObject payload = new JsonObject();
        payload.addProperty("planId", plan.getId());
        payload.addProperty("stripeMerchantId", stripeMerchantId);
        payload.addProperty("paymentIntentId", extractPaymentIntentId(pendingClientSecret));

        mAuth.getCurrentUser().getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().getToken() == null) {
                updateChangeButtonState();
                android.widget.Toast.makeText(requireContext(), "Please sign in again", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            String authToken = task.getResult().getToken();
            new Thread(() -> {
                try {
                    API.PUT(endpoint, authToken, payload);
                    if (!isAdded()) {
                        return;
                    }
                    parent.runOnUiThread(() -> {
                        activePlanId = plan.getId();
                        selectedPlanId = plan.getId();
                        adapter.setActivePlanId(activePlanId);
                        adapter.setSelectedPlanId(selectedPlanId);
                        selectedPlanText.setText("Selected plan: " + plan.getName());
                        updateChangeButtonState();
                        android.widget.Toast.makeText(requireContext(), "Subscription updated", android.widget.Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    if (!isAdded()) {
                        return;
                    }
                    parent.runOnUiThread(() -> {
                        updateChangeButtonState();
                        android.widget.Toast.makeText(requireContext(), "Failed to update subscription", android.widget.Toast.LENGTH_SHORT).show();
                    });
                } finally {
                    pendingPlanChange = null;
                    pendingClientSecret = null;
                }
            }).start();
        });
    }

    private String fetchCurrentSubscriptionPlanId(String token, String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            return null;
        }

        try {
            JsonElement response = API.GET("/subscription", token);
            if (response == null || !response.isJsonObject()) {
                return null;
            }

            JsonObject root = response.getAsJsonObject();
            if (!root.has("subscription") || !root.get("subscription").isJsonObject()) {
                return null;
            }

            JsonObject subscription = root.getAsJsonObject("subscription");
            if (!subscription.has("planId") || !subscription.get("planId").isJsonObject()) {
                return null;
            }

            JsonObject planObj = subscription.getAsJsonObject("planId");
            if (!planObj.has("_id") || planObj.get("_id").isJsonNull()) {
                return null;
            }

            return planObj.get("_id").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Plan findPlanById(String planId) {
        if (planId == null) {
            return null;
        }

        for (Plan plan : loadedPlans) {
            if (planId.equals(plan.getId())) {
                return plan;
            }
        }
        return null;
    }

    private List<Plan> parsePlans(JsonElement response) {
        List<Plan> plans = new ArrayList<>();
        if (response == null || !response.isJsonObject()) {
            return plans;
        }

        JsonObject root = response.getAsJsonObject();
        if (!root.has("plans") || !root.get("plans").isJsonArray()) {
            return plans;
        }

        JsonArray arr = root.getAsJsonArray("plans");
        for (int i = 0; i < arr.size(); i++) {
            JsonElement element = arr.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();

            String id = obj.has("_id") && !obj.get("_id").isJsonNull() ? obj.get("_id").getAsString() : "";
            String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "Plan";
            long storage = obj.has("storageLimit") && !obj.get("storageLimit").isJsonNull() ? obj.get("storageLimit").getAsLong() : 0L;
            double price = obj.has("price") && !obj.get("price").isJsonNull() ? obj.get("price").getAsDouble() : 0d;

            plans.add(new Plan(id, name, storage, price));
        }

        plans.sort(Comparator.comparingDouble(Plan::getPrice));
        return plans;
    }

    private Plan pickDefaultPlan(List<Plan> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }

        for (Plan plan : plans) {
            if (plan.getName() != null && plan.getName().toLowerCase(Locale.US).equals("free")) {
                return plan;
            }
        }

        return plans.get(0);
    }

    private Plan pickInitialPlan(List<Plan> plans, String currentPlanFromApi) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }

        if (currentPlanFromApi != null) {
            for (Plan plan : plans) {
                if (currentPlanFromApi.equals(plan.getId())) {
                    return plan;
                }
            }
        }

        return pickDefaultPlan(plans);
    }

    private void updateChangeButtonState() {
        boolean enabled = selectedPlanId != null && activePlanId != null && !selectedPlanId.equals(activePlanId);
        changeSubscriptionButton.setEnabled(enabled);
        changeSubscriptionButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private long toMinorUnits(double price) {
        return Math.round(price * 100d);
    }

    private String extractPaymentIntentId(String clientSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            return null;
        }
        int idx = clientSecret.indexOf("_secret");
        if (idx <= 0) {
            return clientSecret;
        }
        return clientSecret.substring(0, idx);
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }
}

