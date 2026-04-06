package com.example.lumi.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumi.R;
import com.example.lumi.models.Plan;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PlanViewHolder> {

    public interface OnPlanSelectedListener {
        void onPlanSelected(Plan plan);
    }

    private final Context context;
    private final List<Plan> plans = new ArrayList<>();
    private final OnPlanSelectedListener onPlanSelectedListener;
    private String activePlanId;
    private int selectedIndex = -1;

    public PlanAdapter(Context context, OnPlanSelectedListener onPlanSelectedListener) {
        this.context = context;
        this.onPlanSelectedListener = onPlanSelectedListener;
    }

    public void submitPlans(List<Plan> items) {
        plans.clear();
        if (items != null) {
            plans.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void setSelectedPlanId(String planId) {
        if (planId == null) {
            selectedIndex = -1;
            notifyDataSetChanged();
            return;
        }

        int newIndex = -1;
        for (int i = 0; i < plans.size(); i++) {
            Plan plan = plans.get(i);
            if (planId.equals(plan.getId())) {
                newIndex = i;
                break;
            }
        }

        selectedIndex = newIndex;
        notifyDataSetChanged();
    }

    public void setActivePlanId(String planId) {
        activePlanId = planId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView card = new MaterialCardView(context);
        RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int vm = dp(context, 6);
        cardLp.setMargins(0, vm, 0, vm);
        card.setLayoutParams(cardLp);
        card.setCheckable(true);
        card.setRadius(dp(context, 14));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(context.getResources().getColor(R.color.purple_500, null));
        card.setStrokeWidth(dp(context, 1));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = dp(context, 16);
        body.setPadding(p, p, p, p);

        TextView nameText = new TextView(context);
        nameText.setTextSize(18f);
        nameText.setTextColor(Color.BLACK);
        nameText.setTypeface(nameText.getTypeface(), Typeface.BOLD);

        TextView storageText = new TextView(context);
        storageText.setTextSize(14f);
        storageText.setTextColor(context.getResources().getColor(R.color.gray_600, null));
        LinearLayout.LayoutParams storageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        storageLp.topMargin = dp(context, 8);
        storageText.setLayoutParams(storageLp);

        TextView priceText = new TextView(context);
        priceText.setTextSize(14f);
        priceText.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        priceLp.topMargin = dp(context, 4);
        priceText.setLayoutParams(priceLp);

        body.addView(nameText);
        body.addView(storageText);
        body.addView(priceText);
        card.addView(body);

        return new PlanViewHolder(card, nameText, storageText, priceText);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        Plan plan = plans.get(position);
        boolean active = activePlanId != null && activePlanId.equals(plan.getId());
        holder.nameText.setText(active ? plan.getName() + " (Current)" : plan.getName());
        holder.priceText.setText(formatPrice(plan.getPrice()));
        holder.storageText.setText(formatStorage(plan.getStorageLimit()));

        boolean selected = position == selectedIndex;
        holder.card.setChecked(selected);
        holder.card.setCardBackgroundColor(active
                ? context.getResources().getColor(R.color.purple_200, null)
                : Color.WHITE);
        holder.card.setStrokeWidth(selected ? dp(holder.itemView.getContext(), 2) : dp(holder.itemView.getContext(), 1));

        holder.itemView.setOnClickListener(v -> {
            int previousSelected = selectedIndex;
            selectedIndex = holder.getBindingAdapterPosition();
            if (previousSelected >= 0) {
                notifyItemChanged(previousSelected);
            }
            notifyItemChanged(selectedIndex);
            if (onPlanSelectedListener != null) {
                onPlanSelectedListener.onPlanSelected(plan);
            }
        });
    }

    @Override
    public int getItemCount() {
        return plans.size();
    }

    private String formatStorage(long storageBytes) {
        double gb = storageBytes / (1024d * 1024d * 1024d);
        if (gb >= 1024d) {
            return String.format(Locale.US, "%.0f TB", gb / 1024d);
        }
        return String.format(Locale.US, "%.0f GB", gb);
    }

    private String formatPrice(double price) {
        if (price <= 0d) {
            return "Free";
        }
        return String.format(Locale.US, "$%.0f/month", price);
    }

    private int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    public static class PlanViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView nameText;
        final TextView storageText;
        final TextView priceText;

        public PlanViewHolder(
                @NonNull View itemView,
                TextView nameText,
                TextView storageText,
                TextView priceText
        ) {
            super(itemView);
            card = (MaterialCardView) itemView;
            this.nameText = nameText;
            this.storageText = storageText;
            this.priceText = priceText;
        }
    }
}

