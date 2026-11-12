package com.rakotoplayer.android;

import android.app.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.*;
import android.system.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/**
 * Private bounded image decoding directly from the authenticated descriptor, never a gallery URI.
 */
final class PrivateImageViewer {
  interface Closed {
    void closed(AlertDialog dialog);
  }

  static AlertDialog create(
      Activity activity,
      MediaEntry entry,
      ParcelFileDescriptor fd,
      ExecutorService worker,
      Runnable export,
      Closed closed) {
    FrameLayout root = new FrameLayout(activity);
    root.setBackgroundColor(Color.BLACK);
    ZoomImage image = new ZoomImage(activity);
    root.addView(image, new FrameLayout.LayoutParams(-1, -1));
    TextView status = Ui.text(activity, "Ouverture de l’image…", 14, Color.WHITE);
    status.setGravity(Gravity.CENTER);
    root.addView(status, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout header = new LinearLayout(activity);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setBackgroundColor(0xc0000000);
    TextView title = Ui.text(activity, entry.name, 14, Color.WHITE);
    title.setMaxLines(2);
    header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(activity, 56), 1));
    ImageButton back = Ui.icon(activity, "close", "Fermer l’image", false);
    header.addView(back, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
    root.addView(header, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 56), Gravity.TOP));
    Button restore = Ui.button(activity, "Restaurer dans la galerie", true);
    root.addView(restore, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 48), Gravity.BOTTOM));
    AlertDialog dialog = new AlertDialog.Builder(activity).setView(root).create();
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    Handler main = new Handler(Looper.getMainLooper());
    boolean[] dismissed = {false};
    Future<?>[] pending = {null};
    back.setOnClickListener(v -> dialog.dismiss());
    restore.setOnClickListener(
        v -> {
          dialog.dismiss();
          export.run();
        });
    dialog.setOnDismissListener(
        d -> {
          dismissed[0] = true;
          if (pending[0] != null) pending[0].cancel(true);
          try {
            fd.close();
          } catch (Exception ignored) {
          }
          image.setImageDrawable(null);
          closed.closed(dialog);
        });
    dialog.setOnShowListener(
        d -> {
          dialog.getWindow().setLayout(-1, -1);
          dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        });
    pending[0] =
        worker.submit(
            () -> {
              try {
                int orientation = ExifInterface.ORIENTATION_NORMAL;
                if (Build.VERSION.SDK_INT >= 24) {
                  try {
                    ExifInterface exif = new ExifInterface(fd.getFileDescriptor());
                    orientation =
                        exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                  } catch (Exception ignored) {
                  }
                  Os.lseek(fd.getFileDescriptor(), 0, OsConstants.SEEK_SET);
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
                if (options.outWidth <= 0 || options.outHeight <= 0)
                  throw new IllegalArgumentException(
                      "Ce format d’image ne peut pas être décodé sur ce téléphone. Vous pouvez le"
                          + " restaurer dans la galerie.");
                int sample = 1;
                while (Math.max(options.outWidth, options.outHeight) / sample > 2048) sample *= 2;
                Os.lseek(fd.getFileDescriptor(), 0, OsConstants.SEEK_SET);
                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap =
                    BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
                if (bitmap == null) throw new IllegalArgumentException("Image illisible");
                Matrix transform = new Matrix();
                switch (orientation) {
                  case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    transform.setScale(-1, 1);
                    break;
                  case ExifInterface.ORIENTATION_ROTATE_180:
                    transform.setRotate(180);
                    break;
                  case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    transform.setScale(1, -1);
                    break;
                  case ExifInterface.ORIENTATION_TRANSPOSE:
                    transform.setRotate(90);
                    transform.postScale(-1, 1);
                    break;
                  case ExifInterface.ORIENTATION_ROTATE_90:
                    transform.setRotate(90);
                    break;
                  case ExifInterface.ORIENTATION_TRANSVERSE:
                    transform.setRotate(270);
                    transform.postScale(-1, 1);
                    break;
                  case ExifInterface.ORIENTATION_ROTATE_270:
                    transform.setRotate(270);
                    break;
                }
                if (!transform.isIdentity()) {
                  Bitmap corrected =
                      Bitmap.createBitmap(
                          bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transform, true);
                  if (corrected != bitmap) bitmap.recycle();
                  bitmap = corrected;
                }
                Bitmap ready = bitmap;
                main.post(
                    () -> {
                      if (dismissed[0] || activity.isDestroyed()) {
                        ready.recycle();
                        return;
                      }
                      image.setImageBitmap(ready);
                      image.fit();
                      status.setVisibility(View.GONE);
                    });
              } catch (Exception failure) {
                main.post(
                    () -> {
                      if (!dismissed[0])
                        status.setText(
                            failure.getMessage() == null
                                ? "Image indisponible"
                                : failure.getMessage());
                    });
              }
            });
    return dialog;
  }

  private static final class ZoomImage extends ImageView {
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scale;
    private float zoom = 1, lastX, lastY;

    ZoomImage(Activity context) {
      super(context);
      setScaleType(ScaleType.MATRIX);
      scale =
          new ScaleGestureDetector(
              context,
              new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector gesture) {
                  float factor = Math.max(1, Math.min(6, zoom * gesture.getScaleFactor())) / zoom;
                  zoom *= factor;
                  matrix.postScale(factor, factor, gesture.getFocusX(), gesture.getFocusY());
                  constrain();
                  return true;
                }
              });
    }

    void fit() {
      if (getDrawable() == null || getWidth() == 0) return;
      zoom = 1;
      float ratio =
          Math.min(
              getWidth() / (float) getDrawable().getIntrinsicWidth(),
              getHeight() / (float) getDrawable().getIntrinsicHeight());
      matrix.reset();
      matrix.postScale(ratio, ratio);
      matrix.postTranslate(
          (getWidth() - getDrawable().getIntrinsicWidth() * ratio) / 2,
          (getHeight() - getDrawable().getIntrinsicHeight() * ratio) / 2);
      setImageMatrix(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      super.onSizeChanged(w, h, oldW, oldH);
      fit();
    }

    private void constrain() {
      if (getDrawable() == null) return;
      RectF bounds =
          new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
      matrix.mapRect(bounds);
      float dx =
          bounds.width() < getWidth()
              ? (getWidth() - bounds.width()) / 2 - bounds.left
              : bounds.left > 0
                  ? -bounds.left
                  : bounds.right < getWidth() ? getWidth() - bounds.right : 0;
      float dy =
          bounds.height() < getHeight()
              ? (getHeight() - bounds.height()) / 2 - bounds.top
              : bounds.top > 0
                  ? -bounds.top
                  : bounds.bottom < getHeight() ? getHeight() - bounds.bottom : 0;
      matrix.postTranslate(dx, dy);
      setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      scale.onTouchEvent(event);
      if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE
          && !scale.isInProgress()
          && zoom > 1) {
        matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
        constrain();
      }
      lastX = event.getX();
      lastY = event.getY();
      if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) performClick();
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
