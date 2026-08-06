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
            Insets status = statusInsets(windowInsets);
            v.setPadding(
                    contentLeft + status.left,
                    contentTop + status.top,
                    contentRight + status.right,
                    contentBottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        Runnable apply = () -> {
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(appBar);
            if (rootInsets == null && appBar.getRootView() != null) {
                rootInsets = ViewCompat.getRootWindowInsets(appBar.getRootView());
            }
            if (rootInsets != null) {
                Insets status = statusInsets(rootInsets);
                appBar.setPadding(
                        contentLeft + status.left,
                        contentTop + status.top,
                        contentRight + status.right,
                        contentBottom
                );
            }
            ViewCompat.requestApplyInsets(appBar);
        };

        apply.run();
        appBar.post(apply);
        // One more pass after layout — first insets can arrive late on nested roots.
        appBar.postDelayed(apply, 50);
    }

    @NonNull
    private static Insets statusInsets(@NonNull WindowInsetsCompat windowInsets) {
        Insets status = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
        if (status.top == 0) {
            // Fallback: some devices report only systemBars on first pass
            status = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            return Insets.of(status.left, status.top, status.right, 0);
        }
        return status;
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

        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(target);
        if (rootInsets != null) {
            Insets nav = rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            target.setPadding(contentLeft, contentTop, contentRight, contentBottom + nav.bottom);
        }
        ViewCompat.requestApplyInsets(target);
        target.post(() -> ViewCompat.requestApplyInsets(target));
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
        target.post(() -> ViewCompat.requestApplyInsets(target));
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
            // Pad the app bar directly — do not rely on a parent listener
            // (FrameLayout / nested roots often never deliver the first insets pass).
            applyAppBarInsets(appBar);

            View successAppBar = activity.findViewById(R.id.success_app_bar);
            if (successAppBar != null) {
                applyAppBarInsets(successAppBar);
            }

            if (activity instanceof PdfReaderActivity) {
                View recycler = activity.findViewById(R.id.pdf_recycler_view);
                if (recycler != null) {
                    applyAppBarInsets(recycler);
                }
            }

            if (root != null && root != appBar) {
                applyBottomNavPaddingWithoutStealingStatus(root);
            }
            return;
        }

        if (root != null) {
            applySystemBarPadding(root, true);
        }
    }

    /**
     * Pads only the bottom of {@code root} for the nav bar. Status-bar insets are
     * left alone so child app bars can still consume them.
     */
    private static void applyBottomNavPaddingWithoutStealingStatus(@NonNull View root) {
        final int contentLeft = root.getPaddingLeft();
        final int contentTop = root.getPaddingTop();
        final int contentRight = root.getPaddingRight();
        final int contentBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets nav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(contentLeft, contentTop, contentRight, contentBottom + nav.bottom);
            // Keep status-bar insets available for children (app bars).
            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                    .build();
        });

        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(root);
        if (rootInsets != null) {
            Insets nav = rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            root.setPadding(contentLeft, contentTop, contentRight, contentBottom + nav.bottom);
        }
        ViewCompat.requestApplyInsets(root);
        root.post(() -> ViewCompat.requestApplyInsets(root));
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
            return toolbar;
        }
        return null;
    }

    /** Dark / primary headers need light status icons; white headers need dark icons. */
    static boolean usesLightStatusBars(@NonNull Activity activity) {
        if (isImmersiveCamera(activity)) {
            return false;
        }
        return !(activity instanceof SignPdfActivity
                || activity instanceof ScanReviewActivity
                || activity instanceof ManagePdfPagesActivity
                || activity instanceof DrawSignatureActivity);
    }

    static boolean isImmersiveCamera(@NonNull Activity activity) {
        return activity instanceof CaptureSignatureActivity
                || activity instanceof ScanDocumentActivity;
    }
}
