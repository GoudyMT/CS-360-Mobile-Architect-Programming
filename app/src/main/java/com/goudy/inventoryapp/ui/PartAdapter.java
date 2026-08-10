package com.goudy.inventoryapp.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.goudy.inventoryapp.ItemDetailActivity;
import com.goudy.inventoryapp.R;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.StockStatus;

/**
 * Binds parts to the quick-reference cards. Backed by DiffUtil, so when the list changes only
 * the rows that actually changed are rebound - the grid stays smooth as the database updates.
 * Tapping a card opens its detail, where check out and delete live; the signed-in user is passed
 * along so a checkout there can record who did it.
 */
public class PartAdapter extends ListAdapter<Part, PartAdapter.PartViewHolder> {

    private final long userId;

    public PartAdapter(long userId) {
        super(DIFF);
        this.userId = userId;
    }

    // Lets DiffUtil tell an unchanged row from a moved or edited one without a full redraw.
    private static final DiffUtil.ItemCallback<Part> DIFF = new DiffUtil.ItemCallback<Part>() {
        @Override
        public boolean areItemsTheSame(@NonNull Part a, @NonNull Part b) {
            return a.getId() == b.getId();
        }

        // PartType is an enum, so == is the right value comparison; DiffUtilEquals only expects
        // .equals() for objects and misfires on the enum check.
        @SuppressLint("DiffUtilEquals")
        @Override
        public boolean areContentsTheSame(@NonNull Part a, @NonNull Part b) {
            return a.getQuantity() == b.getQuantity()
                    && a.getName().equals(b.getName())
                    && a.getSystem().equals(b.getSystem())
                    && a.getType() == b.getType()
                    && a.getAuthorizedQty() == b.getAuthorizedQty()
                    && a.getLowThreshold() == b.getLowThreshold();
        }
    };

    @NonNull
    @Override
    public PartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_part_card, parent, false);
        return new PartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PartViewHolder holder, int position) {
        holder.bind(getItem(position), userId);
    }

    static class PartViewHolder extends RecyclerView.ViewHolder {

        private final View statusBar;
        private final TextView partName;
        private final TextView systemTag;
        private final TextView quantityText;
        private final TextView statusLabel;

        PartViewHolder(@NonNull View itemView) {
            super(itemView);
            statusBar = itemView.findViewById(R.id.statusBar);
            partName = itemView.findViewById(R.id.partName);
            systemTag = itemView.findViewById(R.id.systemTag);
            quantityText = itemView.findViewById(R.id.quantityText);
            statusLabel = itemView.findViewById(R.id.statusLabel);
        }

        void bind(Part part, long userId) {
            Context context = itemView.getContext();

            // Tapping a card opens its full detail (check out / delete live there).
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ItemDetailActivity.class);
                intent.putExtra(ItemDetailActivity.EXTRA_PART_ID, part.getId());
                intent.putExtra(ItemDetailActivity.EXTRA_USER_ID, userId);
                context.startActivity(intent);
            });

            partName.setText(part.getName());
            systemTag.setText(part.getSystem());
            quantityText.setText(String.valueOf(part.getQuantity()));

            // The status color/label are driven by the part's derived status, so the
            // card never hard-codes them.
            StockStatus status = part.getStatus();
            statusBar.setBackgroundColor(ContextCompat.getColor(context, barColor(status)));
            int textColor = ContextCompat.getColor(context, textColor(status));
            quantityText.setTextColor(textColor);
            statusLabel.setTextColor(textColor);
            statusLabel.setText(label(status));
        }

        private int barColor(StockStatus status) {
            switch (status) {
                case IN_STOCK: return R.color.stock_in;
                case LOW: return R.color.stock_low;
                default: return R.color.stock_out;
            }
        }

        private int textColor(StockStatus status) {
            switch (status) {
                case IN_STOCK: return R.color.stock_in_on;
                case LOW: return R.color.stock_low_on;
                default: return R.color.stock_out_on;
            }
        }

        private int label(StockStatus status) {
            switch (status) {
                case IN_STOCK: return R.string.status_in_stock;
                case LOW: return R.string.status_low;
                default: return R.string.status_out;
            }
        }
    }
}
