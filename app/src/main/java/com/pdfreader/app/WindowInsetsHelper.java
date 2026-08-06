package com.pdfreader.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;

/**
 * Edge-to-edge + system-bar inset helpers.
 * App bars use the same pattern as My Signatures: keep XML content padding,
 * then add the real status-bar inset on top.
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {}

    /** Call before {@code super.onCreate()} when possible (or from {@code onActivityPreCreated}). */
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
     * Pads an app bar / header with the status-bar inset while preserving
     * its existing XML padding (content spacing).
     */
    public static void applyAppBarInsets(@NonNull View appBar) {
        final int contentLeft = appBar.getPaddingLeft();
        final int contentTop = appBar.getPaddingTop();
        final int contentRight = appBar.getPaddingRight();
        final int contentBottom = appBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, windowInsets) -> {
            Insets status = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    contentLeft + status.left,
                    contentTop + status.top,
                    contentRight + status.right,
                    contentBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(appBar);
    }

    /** Status-bar inset only (preserves existing padding). Alias used by MainActivity. */
    public static void applyStatusBarPadding(@NonNull View target) {
        applyAppBarInsets(target);
    }

    /** Navigation-bar inset on the bottom of a view. */
    public static void applyNavigationBarPadding(@NonNull View target) {
        final int contentLeft = target.getPaddingLeft();
        final int contentTop = target.getPaddingTop();
        final int contentRight = target.getPaddingRight();
        final int contentBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
            Insets nav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(contentLeft, contentTop, contentRight, contentBottom + nav.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(target);
    }

    /** Full system-bar padding on a root view (fallback when there is no app bar). */
    public static void applySystemBarPadding(@NonNull View target, boolean includeNavBar) {
        final int contentLeft = target.getPaddingLeft();
        final int contentTop = target.getPaddingTop();
        final int contentRight = target.getPaddingRight();
        final int contentBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    contentLeft + bars.left,
                    contentTop + bars.top,
                    contentRight + bars.right,
                    contentBottom + (includeNavBar ? bars.bottom : 0)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(target);
    }

    /**
     * Apply after {@code setContentView}. Finds {@code app_bar} / toolbar chrome and
     * pads it like My Signatures; pads the root for the navigation bar.
     */
    public static void setupAfterSetContentView(@NonNull Activity activity) {
        if (activity instanceof MainActivityNew) {
            return; // MainActivityNew wires fragment_container + bottom nav itself
        }

        boolean immersive = isImmersiveCamera(activity);
        enableEdgeToEdge(activity, usesLightStatusBars(activity));

        // Camera hosts: chrome may live in a fragment added after this callback.
        // Don't pad the activity root — fragment / activity chrome handle insets.
        if (immersive) {
            View topBar = activity.findViewById(R.id.top_bar);
            if (topBar != null) {
                applyAppBarInsets(topBar);
            }
            View bottomControls = activity.findViewById(R.id.bottom_controls);
            if (bottomControls != null) {
                applyNavigationBarPadding(bottomControls);
            }
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        View root = ((ViewGroup) content).getChildAt(0);

        View appBar = findAppBar(activity);
        if (appBar != null) {
            // Apply status + nav in one root listener so the parent doesn't
            // intercept insets before the app bar child can use them.
            View successAppBar = activity.findViewById(R.id.success_app_bar);
            View recycler = activity instanceof PdfReaderActivity
                    ? activity.findViewById(R.id.pdf_recycler_view)
                    : null;
            if (root != null) {
                applyAppBarAndNavInsets(root, appBar, successAppBar, recycler);
            } else {
                applyAppBarInsets(appBar);
                if (successAppBar != null) applyAppBarInsets(successAppBar);
                if (recycler != null) applyAppBarInsets(recycler);
            }
            return;
        }

        if (root != null) {
            applySystemBarPadding(root, true);
        }
    }

    /**
     * Pads {@code appBar} with the status-bar inset and {@code root} with the
     * navigation-bar inset from a single listener (avoids parent intercepting).
     */
    private static void applyAppBarAndNavInsets(
            @NonNull View root,
            @NonNull View appBar,
            @Nullable View successAppBar,
            @Nullable View extraStatusTarget) {
        final int rootLeft = root.getPaddingLeft();
        final int rootTop = root.getPaddingTop();
        final int rootRight = root.getPaddingRight();
        final int rootBottom = root.getPaddingBottom();

        final int barLeft = appBar.getPaddingLeft();
        final int barTop = appBar.getPaddingTop();
        final int barRight = appBar.getPaddingRight();
        final int barBottom = appBar.getPaddingBottom();

        final int successLeft = successAppBar != null ? successAppBar.getPaddingLeft() : 0;
        final int successTop = successAppBar != null ? successAppBar.getPaddingTop() : 0;
        final int successRight = successAppBar != null ? successAppBar.getPaddingRight() : 0;
        final int successBottom = successAppBar != null ? successAppBar.getPaddingBottom() : 0;

        final int extraLeft = extraStatusTarget != null ? extraStatusTarget.getPaddingLeft() : 0;
        final int extraTop = extraStatusTarget != null ? extraStatusTarget.getPaddingTop() : 0;
        final int extraRight = extraStatusTarget != null ? extraStatusTarget.getPaddingRight() : 0;
        final int extraBottom = extraStatusTarget != null ? extraStatusTarget.getPaddingBottom() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets status = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets nav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

            appBar.setPadding(
                    barLeft + status.left,
                    barTop + status.top,
                    barRight + status.right,
                    barBottom);

            if (successAppBar != null) {
                successAppBar.setPadding(
                        successLeft + status.left,
                        successTop + status.top,
                        successRight + status.right,
                        successBottom);
            }
            if (extraStatusTarget != null) {
                extraStatusTarget.setPadding(
                        extraLeft + status.left,
                        extraTop + status.top,
                        extraRight + status.right,
                        extraBottom);
            }

            v.setPadding(rootLeft, rootTop, rootRight, rootBottom + nav.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** @deprecated Use {@link #setupAfterSetContentView(Activity)} */
    public static void applyDefault(@NonNull Activity activity) {
        setupAfterSetContentView(activity);
    }

    @Nullable
    private static View findAppBar(@NonNull Activity activity) {
        View appBar = activity.findViewById(R.id.app_bar);
        if (appBar != null) return appBar;

        appBar = activity.findViewById(R.id.top_toolbar);
        if (appBar != null) return appBar;

        appBar = activity.findViewById(R.id.top_bar);
        if (appBar != null) return appBar;

        View toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            ViewParent parent = toolbar.getParent();
            if (parent instanceof AppBarLayout) {
                return (View) parent;
            }
            // LinearLayout toolbars (crop / review) — pad the toolbar itself
            return toolbar;
        }
        return null;
    }

    /** Dark / primary headers need light status icons; white headers need dark icons. */
    static boolean usesLightStatusBars(@NonNull Activity activity) {
        if (isImmersiveCamera(activity)) {
            return false;
        }
        return !(activity instanceof MergePdfActivity
                || activity instanceof SignPdfActivity
                || activity instanceof ScanReviewActivity
                || activity instanceof ManagePdfPagesActivity
                || activity instanceof DrawSignatureActivity
                || activity instanceof ImageToPdfActivity
                || activity instanceof EpubReaderActivity);
    }

    static boolean isImmersiveCamera(@NonNull Activity activity) {
        return activity instanceof CaptureSignatureActivity
                || activity instanceof ScanDocumentActivity;
    }
}
