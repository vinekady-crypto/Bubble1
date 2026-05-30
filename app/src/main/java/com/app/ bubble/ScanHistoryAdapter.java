package com.app.bubble;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.HistoryViewHolder> {

    private List<ScanHistoryHelper.ScanItem> items = new ArrayList<>();
    private OnItemClickListener listener;
    private boolean isSelectionMode = false;

    public interface OnItemClickListener {
        void onItemClick(String text);
        void onDeleteItems(List<String> itemsToDelete); // Callback to delete
    }

    public ScanHistoryAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ScanHistoryHelper.ScanItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void exitSelectionMode() {
        isSelectionMode = false;
        for (ScanHistoryHelper.ScanItem item : items) {
            item.isSelected = false;
        }
        notifyDataSetChanged();
    }

    // Helper to get selected items for deletion
    public void deleteSelected() {
        List<String> toDelete = new ArrayList<>();
        for (ScanHistoryHelper.ScanItem item : items) {
            if (item.isSelected) {
                toDelete.add(item.text);
            }
        }
        if (!toDelete.isEmpty()) {
            listener.onDeleteItems(toDelete);
            exitSelectionMode();
        }
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scan_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        final ScanHistoryHelper.ScanItem item = items.get(position);

        // Set Text
        holder.tvPreview.setText(item.text);

        // Set Date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(item.timestamp)));

        // Handle Selection Mode UI
        if (isSelectionMode) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(item.isSelected);
            holder.btnCopy.setVisibility(View.GONE); // Hide copy btn during selection
        } else {
            holder.checkBox.setVisibility(View.GONE);
            holder.btnCopy.setVisibility(View.VISIBLE);
        }

        // --- CLICK LISTENERS ---

        // 1. Main Click (Toggle selection OR Open Editor)
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                item.isSelected = !item.isSelected;
                notifyItemChanged(position);
            } else {
                listener.onItemClick(item.text);
            }
        });

        // 2. Long Press (Enter Selection Mode)
        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                item.isSelected = true; // Select the one we long-pressed
                notifyDataSetChanged(); // Refresh all rows to show checkboxes
                return true;
            }
            return false;
        });

        // 3. Checkbox Click
        holder.checkBox.setOnClickListener(v -> {
            item.isSelected = holder.checkBox.isChecked();
        });

        // 4. Quick Copy Button
        holder.btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Scanned History", item.text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Copied", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvPreview;
        TextView tvDate;
        CheckBox checkBox;
        ImageButton btnCopy;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPreview = itemView.findViewById(R.id.text_history_preview);
            tvDate = itemView.findViewById(R.id.text_history_date);
            checkBox = itemView.findViewById(R.id.checkbox_history);
            btnCopy = itemView.findViewById(R.id.btn_history_copy);
        }
    }
}