package com.example.duritor;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

public final class ImageViewer {

    private ImageViewer() {
    }

    public static void show(Context context, String imageUrl) {
        if (context == null || imageUrl == null || imageUrl.trim().isEmpty()
                || "null".equalsIgnoreCase(imageUrl.trim())) {
            return;
        }

        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_image_viewer);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        ImageView imageView = dialog.findViewById(R.id.fullscreenImage);
        ImageButton closeButton = dialog.findViewById(R.id.closeButton);

        String sanitizedUrl = imageUrl.trim().replace(" ", "%20");
        Glide.with(context)
                .load(Uri.parse(sanitizedUrl))
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .fitCenter())
                .placeholder(android.R.drawable.ic_menu_camera)
                .error(android.R.drawable.ic_menu_camera)
                .into(imageView);

        Runnable dismiss = dialog::dismiss;
        imageView.setOnClickListener(v -> dismiss.run());
        closeButton.setOnClickListener(v -> dismiss.run());

        dialog.show();
    }
}
