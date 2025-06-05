package com.dstukalov.videoconverterdemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import okhttp3.Response;

public class LibraryActivity extends AppCompatActivity {

    private static final String TAG = "LibraryActivity";

    private LibraryAdapter mAdapter;
    private LibraryViewModel mViewModel;
    private final HashSet<File> mSelectedFiles = new HashSet<>();
    private ActionMode mActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        final ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);

        final RecyclerView recyclerView = findViewById(android.R.id.list);
        final GridLayoutManager layoutManager = new GridLayoutManager(this, 1);
        recyclerView.setLayoutManager(layoutManager);

        final DividerItemDecoration dividerHorz = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.HORIZONTAL);
        final DividerItemDecoration dividerVert = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        final Drawable divider = Objects.requireNonNull(ContextCompat.getDrawable(this, R.drawable.divider));
        dividerHorz.setDrawable(divider);
        dividerVert.setDrawable(divider);
        recyclerView.addItemDecoration(dividerHorz);
        recyclerView.addItemDecoration(dividerVert);

        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(() -> layoutManager.setSpanCount((recyclerView.getWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight()) / recyclerView.getContext().getResources().getDimensionPixelSize(R.dimen.library_item_grid_size)));

        mAdapter = new LibraryAdapter();
        recyclerView.setAdapter(mAdapter);

        mViewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        mViewModel.getFiles().observe(this, files -> {
            Log.i(TAG, "loaded " + (files == null ? -1 : files.size()) + " videos");
            findViewById(R.id.progress).setVisibility(View.GONE);
            final View emptyView = findViewById(android.R.id.empty);
            if (files == null || files.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
            mAdapter.setFiles(files);
        });

        if (savedInstanceState != null) {
            mSelectedFiles.addAll((HashSet<File>)savedInstanceState.getSerializable("selection"));
            if (!mSelectedFiles.isEmpty()) {
                startActionMode();
            }
        }

        try {
            Picasso.setSingletonInstance(new Picasso.Builder(getApplicationContext())
                    .addRequestHandler(new VideoRequestHandler())
                    .downloader(new Downloader() {
                        @Override
                        public @NonNull Response load(@NonNull okhttp3.Request request) throws IOException {
                            throw new IOException();
                        }

                        @Override
                        public void shutdown() {
                        }
                    })
                    .build());
        } catch (IllegalStateException ignore) {}
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i(TAG, "onSaveInstanceState");
        outState.putSerializable("selection", mSelectedFiles);
    }

    private void startActionMode() {
        mActionMode = startSupportActionMode(new androidx.appcompat.view.ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.library_selection, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
                mActionMode = null;
                mSelectedFiles.clear();
                //adapter.notifyDataSetChanged();
                final RecyclerView recyclerView = findViewById(android.R.id.list);
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    final View child = recyclerView.getChildAt(i);
                    child.findViewById(R.id.selection).setVisibility(View.GONE);
                    final View contentView = child.findViewById(R.id.content);
                    if (contentView.getScaleX() != 1f) {
                        contentView.animate().scaleX(1).scaleY(1).setDuration(200);
                    }
                }

            }
        });
        Objects.requireNonNull(mActionMode).setTitle(String.valueOf(mSelectedFiles.size()));
    }

    private static class VideoViewHolder extends RecyclerView.ViewHolder {

        final View contentView;
        final ImageView thumbnailView;
        final TextView durationView;
        final ImageView selectionView;
        final TextView fileNameTextView;

        VideoViewHolder(@NonNull final View itemView) {
            super(itemView);
            contentView = itemView.findViewById(R.id.content);
            thumbnailView = itemView.findViewById(R.id.thumbnail);
            durationView = itemView.findViewById(R.id.duration);
            selectionView = itemView.findViewById(R.id.selection);
            fileNameTextView = itemView.findViewById(R.id.filename);
        }
    }

    public static final DiffUtil.ItemCallback<File> DIFF_CALLBACK = new DiffUtil.ItemCallback<File>() {

        @Override
        public boolean areItemsTheSame(@NonNull File oldItem, @NonNull File newItem) {
            return Objects.equals(oldItem, newItem);
        }
        @Override
        public boolean areContentsTheSame(@NonNull File oldItem, @NonNull File newItem) {
            return Objects.equals(oldItem, newItem);
        }
    };

    private class LibraryAdapter extends RecyclerView.Adapter<VideoViewHolder> {

        private final AsyncListDiffer<File> differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);

        @Override
        public @NonNull VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VideoViewHolder((LayoutInflater.from(getBaseContext()).inflate(R.layout.library_item, parent, false)));
        }

        void setFiles(List<File> files) {
            if (files == null) {
                differ.submitList(Collections.emptyList());
            } else {
                differ.submitList(files);
            }
        }


        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            final File file = differ.getCurrentList().get(position);
            if (holder.fileNameTextView != null) {
                holder.fileNameTextView.setText(file.getName());
            }
            holder.contentView.setOnClickListener(v -> onItemClicked(holder, file));
            holder.contentView.setOnLongClickListener(v -> {
                onItemLongClicked(holder, file);
                return true;
            });

            final float scale;
            if (mSelectedFiles.isEmpty()) {
                holder.selectionView.setVisibility(View.GONE);
                scale = 1f;
            } else {
                holder.selectionView.setVisibility(View.VISIBLE);
                if (mSelectedFiles.contains(file)) {
                    scale = .8f;
                    holder.selectionView.setImageResource(R.drawable.selection_yes);
                } else {
                    scale = 1f;
                    holder.selectionView.setImageResource(R.drawable.selection_no);
                }
            }
            holder.contentView.setScaleX(scale);
            holder.contentView.setScaleY(scale);

            final int size = getResources().getDimensionPixelSize(R.dimen.library_item_grid_size);
            final Uri uri = Uri.fromFile(file);
            holder.durationView.setText("");
            // Define your desired corner radius in dp
            float cornerRadiusDp = 16f; // For example, 8dp. Adjust as you like.

            Picasso.get().load(uri)
                    .resize(size, size)
                    .centerCrop()
                    .transform(new RoundedCornersTransformation(cornerRadiusDp))
                    .into(holder.thumbnailView, new Callback() {
                        @Override
                        public void onSuccess() {
                            final Integer duration = VideoRequestHandler.DURATION_CACHE.get(uri);
                            if (duration != null) {
                                holder.durationView.setText(DateUtils.formatElapsedTime(duration));
                            } else {
                                holder.durationView.setText("");
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            holder.durationView.setText("");
                            holder.fileNameTextView.setText("");
                        }
                    });
        }

        @Override
        public int getItemCount() {
            return differ.getCurrentList().size();
        }

        private void onItemClicked(@NonNull VideoViewHolder holder, @NonNull File file) {
            if (mSelectedFiles.isEmpty()) {
                final PopupMenu popup = new PopupMenu(LibraryActivity.this, holder.itemView);
                popup.getMenuInflater().inflate(R.menu.library_item, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    final int itemId = item.getItemId();
                    if (itemId == R.id.id_play) {
                        final Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), MainActivity.FILE_PROVIDER_AUTHORITY, file), "video/*");
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } else if (itemId == R.id.id_share) {
                        final Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("video/*");
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getBaseContext(), MainActivity.FILE_PROVIDER_AUTHORITY, file));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } else if (itemId == R.id.id_delete) {
                        mViewModel.deleteFile(file);
                    }
                    return true;
                });
                popup.show();
            } else {
                toggleSelection(holder, file);
            }
        }

        private void onItemLongClicked(@NonNull VideoViewHolder holder, @NonNull File file) {
            toggleSelection(holder, file);
        }

        private void toggleSelection(@NonNull VideoViewHolder holder, @NonNull File file) {
            if (mSelectedFiles.remove(file)) {
                holder.contentView.animate().scaleX(1f).scaleY(1f).setDuration(200);
                holder.selectionView.setImageResource(R.drawable.selection_no);
            } else {
                holder.contentView.animate().scaleX(.8f).scaleY(.8f).setDuration(200);
                holder.selectionView.setImageResource(R.drawable.selection_yes);
                mSelectedFiles.add(file);
            }
            if (mSelectedFiles.isEmpty()) {
                if (mActionMode != null) {
                    mActionMode.finish();
                }
            } else {
                if (mActionMode == null) {
                    startActionMode();
                    final RecyclerView recyclerView = findViewById(android.R.id.list);
                    for (int i = 0; i < recyclerView.getChildCount(); i++) {
                        recyclerView.getChildAt(i).findViewById(R.id.selection).setVisibility(View.VISIBLE);
                    }
                }
                Objects.requireNonNull(mActionMode).setTitle(String.valueOf(mSelectedFiles.size()));
            }
        }
    }

    public static class VideoRequestHandler extends RequestHandler {

        static LruCache<Uri, Integer> DURATION_CACHE = new LruCache<>(256);

        @Override
        public boolean canHandleRequest(Request data) {
            return true;
        }

        @Override
        public Result load(Request request, int networkPolicy) {
            final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(request.uri.getPath());
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= 27) {
                try {
                    final int originalWidth = Integer.parseInt(Objects.requireNonNull(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
                    final int originalHeight = Integer.parseInt(Objects.requireNonNull(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
                    final float scaleFactor = Math.max(1f * request.targetWidth / originalWidth, 1f * request.targetHeight / originalHeight);
                    final int decodeWidth = Math.round(scaleFactor * originalWidth);
                    final int decodeHeight = Math.round(scaleFactor * originalHeight);
                    bitmap = mediaMetadataRetriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, decodeWidth, decodeHeight);
                } catch (Exception ex) {
                    bitmap = mediaMetadataRetriever.getFrameAtTime();
                }
            } else {
                bitmap = mediaMetadataRetriever.getFrameAtTime();
            }
            try {
                DURATION_CACHE.put(request.uri, Integer.parseInt(Objects.requireNonNull(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))) / 1000);
            } catch (Exception ignore) {
            }

            try {
                mediaMetadataRetriever.release();
            } catch (
                    IOException e) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
    }
            return bitmap == null ? null : new Result(bitmap, Picasso.LoadedFrom.DISK);
        }
    }
}