package com.rakotoplayer.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.*;

final class Ui {
  static int BLUE = Color.rgb(181, 68, 46),
      NAVY = Color.rgb(18, 59, 99),
      INK = Color.rgb(19, 46, 72),
      MUTED = Color.rgb(100, 116, 139),
      PAPER = Color.rgb(248, 250, 252),
      SOFT = Color.rgb(239, 246, 255);

  static int dp(Context c, float value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable background(int color, int radius, Context c) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(dp(c, radius));
    return shape;
  }

  static TextView text(Context c, String value, float size, int color) {
    TextView view = new TextView(c);
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setFontFeatureSettings("kern");
    return view;
  }

  static Button button(Context c, String value, boolean primary) {
    Button view = new Button(c);
    view.setText(value);
    view.setAllCaps(false);
    view.setTextSize(13);
    view.setTextColor(primary ? Color.WHITE : BLUE);
    view.setBackground(background(primary ? BLUE : SOFT, 14, c));
    view.setMinHeight(dp(c, 48));
    view.setMinimumWidth(0);
    view.setPadding(dp(c, 16), 0, dp(c, 16), 0);
    return view;
  }

  static ImageButton icon(Context c, String type, String label, boolean primary) {
    ImageButton view = new ImageButton(c);
    view.setImageDrawable(new Glyph(type, primary ? Color.WHITE : BLUE));
    view.setContentDescription(label);
    view.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
    view.setBackground(background(primary ? BLUE : SOFT, 16, c));
    view.setMinimumHeight(dp(c, 48));
    view.setMinimumWidth(dp(c, 48));
    return view;
  }

  static void icon(ImageButton view, String type, boolean primary) {
    view.setImageDrawable(new Glyph(type, primary ? Color.WHITE : BLUE));
  }

  static String time(long millis) {
    long seconds = Math.max(0, millis) / 1000;
    return seconds >= 3600
        ? String.format(
            java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  static class Glyph extends Drawable {
    private final String kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(String kind, int color) {
      this.kind = kind;
      paint.setColor(color);
      paint.setStrokeWidth(1.8f);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
      canvas.save();
      Rect bounds = getBounds();
      canvas.translate(bounds.left, bounds.top);
      canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
      paint.setStyle(Paint.Style.STROKE);
      Path p = new Path();
      switch (kind) {
        case "scan":
          canvas.drawArc(4, 4, 20, 20, 45, 280, false, paint);
          canvas.drawLine(20, 5, 20, 11, paint);
          canvas.drawLine(20, 11, 14, 11, paint);
          break;
        case "settings":
          canvas.drawCircle(12, 12, 7, paint);
          canvas.drawCircle(12, 12, 2.5f, paint);
          for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            canvas.drawLine(
                12 + (float) Math.cos(a) * 8,
                12 + (float) Math.sin(a) * 8,
                12 + (float) Math.cos(a) * 10,
                12 + (float) Math.sin(a) * 10,
                paint);
          }
          break;
        case "shuffle":
          canvas.drawLine(3, 5, 8, 5, paint);
          canvas.drawLine(8, 5, 16, 19, paint);
          canvas.drawLine(16, 19, 21, 19, paint);
          canvas.drawLine(3, 19, 8, 19, paint);
          canvas.drawLine(8, 19, 16, 5, paint);
          canvas.drawLine(16, 5, 21, 5, paint);
          canvas.drawLine(18, 2, 21, 5, paint);
          canvas.drawLine(21, 5, 18, 8, paint);
          canvas.drawLine(18, 16, 21, 19, paint);
          canvas.drawLine(21, 19, 18, 22, paint);
          break;
        case "repeat":
          p.moveTo(4, 10);
          p.lineTo(4, 7);
          p.quadTo(4, 4, 7, 4);
          p.lineTo(20, 4);
          p.lineTo(17, 1);
          p.moveTo(20, 4);
          p.lineTo(17, 7);
          p.moveTo(20, 14);
          p.lineTo(20, 17);
          p.quadTo(20, 20, 17, 20);
          p.lineTo(4, 20);
          p.lineTo(7, 17);
          p.moveTo(4, 20);
          p.lineTo(7, 23);
          canvas.drawPath(p, paint);
          break;
        case "play":
          p.moveTo(8, 4);
          p.lineTo(20, 12);
          p.lineTo(8, 20);
          p.close();
          paint.setStyle(Paint.Style.FILL);
          canvas.drawPath(p, paint);
          break;
        case "pause":
          paint.setStyle(Paint.Style.FILL);
          canvas.drawRoundRect(6, 4, 10, 20, 1, 1, paint);
          canvas.drawRoundRect(14, 4, 18, 20, 1, 1, paint);
          break;
        case "next":
          p.moveTo(5, 5);
          p.lineTo(16, 12);
          p.lineTo(5, 19);
          p.close();
          canvas.drawPath(p, paint);
          canvas.drawLine(19, 5, 19, 19, paint);
          break;
        case "previous":
          p.moveTo(19, 5);
          p.lineTo(8, 12);
          p.lineTo(19, 19);
          p.close();
          canvas.drawPath(p, paint);
          canvas.drawLine(5, 5, 5, 19, paint);
          break;
        case "back":
          canvas.drawLine(4, 12, 20, 12, paint);
          canvas.drawLine(4, 12, 11, 5, paint);
          canvas.drawLine(4, 12, 11, 19, paint);
          break;
        case "expand":
          canvas.drawLine(4, 10, 4, 4, paint);
          canvas.drawLine(4, 4, 10, 4, paint);
          canvas.drawLine(14, 20, 20, 20, paint);
          canvas.drawLine(20, 20, 20, 14, paint);
          canvas.drawLine(4, 4, 10, 10, paint);
          canvas.drawLine(20, 20, 14, 14, paint);
          break;
        case "more":
          paint.setStyle(Paint.Style.FILL);
          for (int y = 5; y <= 19; y += 7) canvas.drawCircle(12, y, 1.7f, paint);
          break;
        case "close":
          canvas.drawLine(5, 5, 19, 19, paint);
          canvas.drawLine(19, 5, 5, 19, paint);
          break;
        case "plus":
          canvas.drawLine(4, 12, 20, 12, paint);
          canvas.drawLine(12, 4, 12, 20, paint);
          break;
        case "lock":
          canvas.drawRoundRect(5, 10, 19, 21, 3, 3, paint);
          p.moveTo(8, 10);
          p.lineTo(8, 7);
          p.cubicTo(8, 1, 16, 1, 16, 7);
          p.lineTo(16, 10);
          canvas.drawPath(p, paint);
          canvas.drawLine(12, 14, 12, 17, paint);
          break;
        case "person":
          canvas.drawCircle(12, 7, 4, paint);
          p.moveTo(4, 21);
          p.cubicTo(4, 12, 20, 12, 20, 21);
          canvas.drawPath(p, paint);
          break;
        case "playlist":
          canvas.drawLine(3, 5, 20, 5, paint);
          canvas.drawLine(3, 11, 13, 11, paint);
          canvas.drawLine(3, 17, 10, 17, paint);
          canvas.drawLine(19, 11, 19, 19, paint);
          canvas.drawOval(13, 17, 19, 22, paint);
          break;
        case "folder":
          p.moveTo(3, 7);
          p.lineTo(3, 20);
          p.lineTo(21, 20);
          p.lineTo(21, 7);
          p.lineTo(12, 7);
          p.lineTo(10, 4);
          p.lineTo(3, 4);
          p.close();
          canvas.drawPath(p, paint);
          break;
        case "favorite":
          p.moveTo(12, 21);
          p.cubicTo(2, 14, 0, 9, 5, 5);
          p.cubicTo(8, 3, 10, 5, 12, 7);
          p.cubicTo(14, 5, 16, 3, 19, 5);
          p.cubicTo(24, 9, 22, 14, 12, 21);
          canvas.drawPath(p, paint);
          break;
        case "music":
          canvas.drawLine(9, 18, 9, 5, paint);
          canvas.drawLine(9, 5, 20, 3, paint);
          canvas.drawLine(20, 3, 20, 16, paint);
          canvas.drawOval(3, 15, 9, 21, paint);
          canvas.drawOval(14, 13, 20, 19, paint);
          break;
        default:
          canvas.drawRoundRect(3, 4, 21, 20, 3, 3, paint);
          p.moveTo(10, 9);
          p.lineTo(15, 12);
          p.lineTo(10, 15);
          p.close();
          canvas.drawPath(p, paint);
      }
      canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
      paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  static class Landscape extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Landscape(Context c) {
      super(c);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      int w = getWidth(), h = getHeight();
      paint.setShader(
          new LinearGradient(0, 0, w, h, 0xffd5ebff, 0xffb8dfff, Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
      int[] colors = {0xff8db5c8, 0xff5a899f, 0xff386b80, 0xff386641};
      for (int layer = 0; layer < 4; layer++) {
        Path p = new Path();
        p.moveTo(0, h);
        for (int x = 0; x <= w + 10; x += 10) {
          float y =
              (float)
                  (h * (.55 + layer * .11)
                      + Math.sin(x / (double) Math.max(1, w) * 5 + layer * .7) * h * .09);
          p.lineTo(x, y);
        }
        p.lineTo(w, h);
        p.close();
        paint.setColor(colors[layer]);
        canvas.drawPath(p, paint);
      }
    }
  }
}
