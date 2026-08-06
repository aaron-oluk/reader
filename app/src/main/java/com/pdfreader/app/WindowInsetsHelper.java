package com.pdfreader.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge + system-bar inset helpers.
 * Use real status/navigation bar heights instead of hardcoded paddingTop.
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {}

    /** Draw behind system bars and style icon contrast. */
    public static void enableEdgeToEdge(@NonNull Activity activity, boolean lightStatusBars) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(lightStatusBars);
            controller.setAppearanceLightNavigationBars(lightStatusBars);
        }
    }

    /**
     * Pads {@code target} with status-bar (top) and optionally navigation-bar (bottom) insets,
     * on top of any existing XML padding (content spacing).
     */
    public static void applySystemBarPadding(@NonNull View target, boolean includeNavBar) {
        final int baseLeft = target.getPaddingLeft();
        final int baseTop = target.getPaddingTop();
        final int baseRight = target.getPaddingRight();
        final int baseBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom + (includeNavBar ? bars.bottom : 0)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(target);
    }

    /** Status-bar inset only (e.g. fragment container above bottom nav). */
    public static void applyStatusBarPadding(@NonNull View target) {
        final int baseLeft = target.getPaddingLeft();
        final int baseTop = target.getPaddingTop();
        final int baseRight = target.getPaddingRight();
        final int baseBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
            Insets status = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    baseLeft + status.left,
                    baseTop + status.top,
                    baseRight + status.right,
                    baseBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(target);
    }

    /** Navigation-bar inset on the bottom of a view (e.g. bottom navigation). */
    public static void applyNavigationBarPadding(@NonNull View target) {
        final int baseLeft = target.getPaddingLeft();
        final int baseTop = target.getPaddingTop();
        final int baseRight = target.getPaddingRight();
        final int baseBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
            Insets nav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(baseLeft, baseTop, baseRight, baseBottom + nav.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(target);
    }

    /**
     * Default for secondary screens: edge-to-edge + pad the content root with system bars.
     * Camera / immersive screens get edge-to-edge with dark icons and padded chrome only.
     */
    public static void applyDefault(@NonNull Activity activity) {
        if (isImmersiveCamera(activity)) {
            enableEdgeToEdge(activity, false);
            View topBar = activity.findViewById(R.id.top_bar);
            if (topBar != null) {
                applyStatusBarPadding(topBar);
            }
            View bottomControls = activity.findViewById(R.id.bottom_controls);
            if (bottomControls != null) {
                applyNavigationBarPadding(bottomControls);
            }
            return;
        }
        if (activity instanceof MainActivityNew
                || activity instanceof SignatureManagementActivity) {
            return; // handled explicitly in those activities
        }

        enableEdgeToEdge(activity, true);
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        View root = ((ViewGroup) content).getChildAt(0);
        if (root == null) return;

        // Prefer a dedicated app bar so the header clears the status bar (My Signatures, etc.)
        View appBar = activity.findViewById(R.id.app_bar);
        if (appBar != null) {
            applyStatusBarPadding(appBar);
            applyNavigationBarPadding(root);
            return;
        }

        // Readers keep floating chrome; pad toolbar + list clearance with status insets.
        View toolbar = activity.findViewById(R.id.top_toolbar);
        if (toolbar != null) {
            applyStatusBarPadding(toolbar);
            View recycler = activity.findViewById(R.id.pdf_recycler_view);
            if (recycler != null) {
                applyStatusBarPadding(recycler);
            }
            applyNavigationBarPadding(root);
        } else {
            applySystemBarPadding(root, true);
        }
    }

    private static boolean isImmersiveCamera(@NonNull Activity activity) {
        return activity instanceof CaptureSignatureActivity
                || activity instanceof ScanDocumentActivity;
    }
}
