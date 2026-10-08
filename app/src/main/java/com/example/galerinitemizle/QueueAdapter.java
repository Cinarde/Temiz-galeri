package com.example.galerinitemizle;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;

/** RecyclerView only decodes visible thumbnails, regardless of queue length. */
public final class QueueAdapter extends ListAdapter<GalleryMedia, QueueAdapter.Holder> {
    interface RestoreListener { void restore(String uri); }
    private final RestoreListener listener;
    private boolean actionsEnabled = true;

    QueueAdapter(RestoreListener listener) {
        super(new DiffUtil.ItemCallback<GalleryMedia>() {
            @Override public boolean areItemsTheSame(@NonNull GalleryMedia oldItem, @NonNull GalleryMedia newItem) { return oldItem.uri.equals(newItem.uri); }
            @Override public boolean areContentsTheSame(@NonNull GalleryMedia oldItem, @NonNull GalleryMedia newItem) { return oldItem.equals(newItem); }
        });
        this.listener = listener;
    }

    void setActionsEnabled(boolean enabled) {
        if (actionsEnabled == enabled) return;
        actionsEnabled = enabled;
        notifyItemRangeChanged(0, getItemCount(), "enabled");
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue_photo, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        GalleryMedia item = getItem(position);
        String uri = item.uri;
        holder.label.setText(SwipeDeckView.dateLabel(holder.itemView.getContext(), item));
        Glide.with(holder.image).asBitmap().load(Uri.parse(uri)).override(480, 480)
                .centerCrop().diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.ic_gallery).error(R.drawable.ic_gallery).into(holder.image);
        holder.restore.setEnabled(actionsEnabled);
        holder.restore.setContentDescription(holder.itemView.getContext().getString(R.string.restore_description, position + 1));
        holder.restore.setOnClickListener(view -> {
            int current = holder.getBindingAdapterPosition();
            if (actionsEnabled && current != RecyclerView.NO_POSITION) listener.restore(getItem(current).uri);
        });
    }

    @Override public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.image).clear(holder.image);
        super.onViewRecycled(holder);
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final MaterialButton restore;
        final TextView label;
        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.queueImage);
            label = view.findViewById(R.id.queueMediaLabel);
            restore = view.findViewById(R.id.restoreButton);
        }
    }
}
