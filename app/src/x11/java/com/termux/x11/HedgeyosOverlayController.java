package com.termux.x11;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.HedgeyosPowerPolicy;
import com.termux.app.HedgeyosRuntimeService;

final class HedgeyosOverlayController {

    interface Actions {
        void openTerminal();
        void runAptCheck();
        void showRuntimeReport();
        void showLogs();
        void restartDesktop();
        void stopDesktop();
        void resetDebian();
        void toggleKeyboard();
        void showAndroidApps();
        void openAndroidSettings();
        void openDisplaySettings();
        void chooseHomeApp();
    }

    private static final String PREFS_NAME = "hedgeyos_overlay";
    private static final String KEY_X = "normalized_x";
    private static final String KEY_Y = "normalized_y";
    private static final int HEDGEHOG_SIZE_DP = 96;
    private static final int EDGE_MARGIN_DP = 8;

    private final Activity activity;
    private final FrameLayout content;
    private final Actions actions;
    private final SharedPreferences preferences;
    private final int touchSlop;

    private ImageView hedgehog;
    private PopupWindow popup;
    private boolean dragging;
    private boolean positioned;
    private boolean reopenPowerGuide;
    private boolean onboardingPromptedWithFocus;

    HedgeyosOverlayController(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
        this.content = activity.findViewById(android.R.id.content);
        this.preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    void install() {
        if (hedgehog != null) {
            return;
        }

        hedgehog = new ImageView(activity);
        hedgehog.setImageResource(R.drawable.hedgeyos_companion);
        hedgehog.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hedgehog.setBackgroundColor(Color.TRANSPARENT);
        hedgehog.setContentDescription("Open hedgeyos controls");
        hedgehog.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hedgehog.setTooltipText("hedgeyos controls");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hedgehog.setElevation(dp(16));
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            dp(HEDGEHOG_SIZE_DP),
            dp(HEDGEHOG_SIZE_DP),
            Gravity.TOP | Gravity.START);
        content.addView(hedgehog, params);
        setupDragAndTap();

        content.addOnLayoutChangeListener((view, left, top, right, bottom,
                                           oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                positioned = false;
                dismissPopup();
            }
            positionFromPreferences();
        });
        content.post(this::positionFromPreferences);
        content.postDelayed(this::showOnboardingIfNeeded, 1000);
    }

    void onResume() {
        if (hedgehog == null) {
            install();
        }
        onboardingPromptedWithFocus = false;
        hedgehog.setVisibility(View.VISIBLE);
        hedgehog.bringToFront();
        content.post(() -> {
            positionFromPreferences();
            if (reopenPowerGuide) {
                reopenPowerGuide = false;
                onboardingPromptedWithFocus = true;
                showPowerGuide();
            }
        });
    }

    void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus || hedgehog == null) {
            return;
        }
        content.post(this::showOnboardingIfNeeded);
    }

    private void showOnboardingIfNeeded() {
        if (hedgehog == null || onboardingPromptedWithFocus ||
            HedgeyosPowerPolicy.isOnboardingComplete(activity)) {
            return;
        }
        onboardingPromptedWithFocus = true;
        showPowerGuide();
    }

    void onPause() {
        dismissPopup();
    }

    void destroy() {
        dismissPopup();
        if (hedgehog != null && hedgehog.getParent() == content) {
            content.removeView(hedgehog);
        }
        hedgehog = null;
    }

    private void setupDragAndTap() {
        final float[] downRaw = new float[2];
        final int[] downPosition = new int[2];

        hedgehog.setOnTouchListener((view, event) -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) hedgehog.getLayoutParams();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = event.getRawX();
                    downRaw[1] = event.getRawY();
                    downPosition[0] = params.leftMargin;
                    downPosition[1] = params.topMargin;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downRaw[0]);
                    int dy = Math.round(event.getRawY() - downRaw[1]);
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        dragging = true;
                        dismissPopup();
                    }
                    if (dragging) {
                        setPosition(downPosition[0] + dx, downPosition[1] + dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        saveNormalizedPosition();
                    } else {
                        showControlMenu();
                    }
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        saveNormalizedPosition();
                    }
                    dragging = false;
                    return true;
                default:
                    return false;
            }
        });
    }

    void showControlMenu() {
        LinearLayout body = verticalLayout();
        body.addView(bodyText("hedgeyos controls stay attached to the desktop. Drag the hedgehog to reposition it."));
        body.addView(actionButton("Background survival setup", () -> showPowerGuide()));
        body.addView(actionButton("Open Debian Terminal", actions::openTerminal));
        body.addView(actionButton("Run Debian APT Check", actions::runAptCheck));
        body.addView(actionButton("Linux Runtime Report", actions::showRuntimeReport));
        body.addView(actionButton("Open hedgeyos logs", actions::showLogs));
        body.addView(actionButton("Restart Desktop", actions::restartDesktop));
        body.addView(actionButton("Stop Desktop", actions::stopDesktop));
        body.addView(actionButton("Reset Debian", actions::resetDebian));
        body.addView(actionButton("Toggle Soft Keyboard", () -> {
            dismissPopup();
            content.postDelayed(actions::toggleKeyboard, 150);
        }));
        body.addView(actionButton("Android Apps", actions::showAndroidApps));
        body.addView(actionButton("Android Settings", actions::openAndroidSettings));
        body.addView(actionButton("Display Settings", actions::openDisplaySettings));
        body.addView(actionButton("Choose Home App", actions::chooseHomeApp));
        showMiniWindow("hedgeyos", body, false);
    }

    private void showPowerGuide() {
        dismissPopup();
        LinearLayout body = verticalLayout();
        body.addView(bodyText(
            "Android and phone-vendor power managers can stop the Linux desktop while it is in the background. " +
                "hedgeyos holds its own wake lock and foreground service while the desktop is running, but the settings below still matter."));

        body.addView(sectionTitle("Current status"));
        body.addView(statusLine("Runtime wake lock",
            HedgeyosRuntimeService.isRuntimeWakeLockHeld(),
            "managed while the desktop is running"));
        body.addView(statusLine("Battery optimization exemption",
            HedgeyosPowerPolicy.isIgnoringBatteryOptimizations(activity),
            "not unrestricted"));
        body.addView(statusLine("Android background restriction",
            !HedgeyosPowerPolicy.isBackgroundRestricted(activity),
            "background use is restricted"));
        body.addView(statusLine("Runtime notifications",
            HedgeyosPowerPolicy.areNotificationsEnabled(activity),
            "notifications are disabled"));

        body.addView(actionButton("Allow unrestricted battery", () -> {
            reopenPowerGuide = true;
            dismissPopup();
            if (!HedgeyosPowerPolicy.requestBatteryOptimizationExemption(activity)) {
                HedgeyosPowerPolicy.openBatterySettings(activity);
            }
        }));
        body.addView(actionButton("Open battery settings", () -> {
            reopenPowerGuide = true;
            dismissPopup();
            HedgeyosPowerPolicy.openBatterySettings(activity);
        }));
        body.addView(actionButton("Open notification settings", () -> {
            reopenPowerGuide = true;
            dismissPopup();
            HedgeyosPowerPolicy.openNotificationSettings(activity);
        }));

        body.addView(sectionTitle(HedgeyosPowerPolicy.manufacturerLabel() + " phone guidance"));
        body.addView(bodyText(HedgeyosPowerPolicy.vendorInstructions()));
        body.addView(actionButton("Open phone background settings", () -> {
            reopenPowerGuide = true;
            dismissPopup();
            if (!HedgeyosPowerPolicy.openVendorBackgroundSettings(activity)) {
                Toast.makeText(activity, "Open hedgeyos App info and Battery settings manually.", Toast.LENGTH_LONG).show();
            }
        }));

        CheckBox reviewed = new CheckBox(activity);
        reviewed.setText("I reviewed my phone's background and autostart settings");
        reviewed.setTextColor(Color.rgb(30, 30, 30));
        reviewed.setTextSize(12);
        reviewed.setChecked(HedgeyosPowerPolicy.wasManualBackgroundSetupReviewed(activity));
        body.addView(reviewed, matchWrapMargins(0, dp(8), 0, dp(8)));

        TextView warning = bodyText(
            "You may continue now. If these settings are incomplete, hedgeyos can still start, but Android may later kill the desktop.");
        warning.setTextColor(Color.rgb(128, 47, 31));
        body.addView(warning);
        body.addView(actionButton("Continue with limited reliability", () -> {
            HedgeyosPowerPolicy.completeOnboarding(activity, reviewed.isChecked());
            dismissPopup();
        }));
        showMiniWindow("Background setup", body, true);
    }

    private void showMiniWindow(String title, LinearLayout body, boolean powerGuide) {
        dismissPopup();

        int popupWidth = Math.min(dp(310),
            Math.max(dp(230), content.getWidth() - dp(24)));
        int availableHeight = content.getHeight() > 0
            ? content.getHeight()
            : activity.getResources().getDisplayMetrics().heightPixels;
        int popupHeight = Math.min(dp(powerGuide ? 440 : 470),
            Math.max(dp(230), availableHeight - dp(48)));

        LinearLayout window = new LinearLayout(activity);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setClipChildren(false);

        LinearLayout titleBar = new LinearLayout(activity);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(7), dp(3), dp(4), dp(3));
        titleBar.setBackgroundResource(R.drawable.hedgeyos_mini_title);

        titleBar.addView(titleSquare());
        View secondSquare = titleSquare();
        LinearLayout.LayoutParams secondSquareParams =
            new LinearLayout.LayoutParams(dp(8), dp(8));
        secondSquareParams.setMargins(dp(3), 0, 0, 0);
        titleBar.addView(secondSquare, secondSquareParams);

        TextView titleText = new TextView(activity);
        titleText.setText(title);
        titleText.setTextColor(Color.rgb(17, 17, 17));
        titleText.setTextSize(12);
        titleText.setTypeface(titleText.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams =
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(8), 0, dp(4), 0);
        titleBar.addView(titleText, titleParams);

        ImageButton close = new ImageButton(activity);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setPadding(dp(2), dp(2), dp(2), dp(2));
        close.setContentDescription("Close mini-window");
        close.setOnClickListener(view -> dismissPopup());
        titleBar.addView(close, new LinearLayout.LayoutParams(dp(26), dp(26)));
        window.addView(titleBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackgroundResource(R.drawable.hedgeyos_mini_window_body);
        scroll.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(body, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        window.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        popup = new PopupWindow(window, popupWidth, popupHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(dp(12));
        }
        popup.setOnDismissListener(() -> popup = null);

        FrameLayout.LayoutParams hedgeParams =
            (FrameLayout.LayoutParams) hedgehog.getLayoutParams();
        int margin = dp(EDGE_MARGIN_DP);
        int rootWidth = Math.max(content.getWidth(), popupWidth + (margin * 2));
        int rootHeight = Math.max(content.getHeight(), popupHeight + (margin * 2));
        int popupX = clamp(hedgeParams.leftMargin + hedgehog.getWidth() - popupWidth,
            margin, Math.max(margin, rootWidth - popupWidth - margin));
        int belowY = hedgeParams.topMargin + hedgehog.getHeight() + dp(6);
        int popupY = belowY + popupHeight <= rootHeight - margin
            ? belowY
            : Math.max(margin, hedgeParams.topMargin - popupHeight - dp(6));
        popup.showAtLocation(content, Gravity.TOP | Gravity.START, popupX, popupY);
    }

    private Button actionButton(String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(25, 25, 25));
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundResource(R.drawable.hedgeyos_mini_button);
        button.setOnClickListener(view -> action.run());
        button.setLayoutParams(matchWrapMargins(0, 0, 0, dp(6)));
        button.getLayoutParams().height = dp(40);
        return button;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(25, 25, 25));
        view.setTextSize(12);
        view.setLineSpacing(dp(2), 1f);
        view.setPadding(dp(6), dp(6), dp(6), dp(6));
        view.setBackgroundColor(Color.rgb(255, 248, 230));
        view.setLayoutParams(matchWrapMargins(0, 0, 0, dp(8)));
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(50, 42, 32));
        view.setTextSize(13);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setLayoutParams(matchWrapMargins(dp(2), dp(5), 0, dp(4)));
        return view;
    }

    private TextView statusLine(String label, boolean okay, String failureText) {
        TextView view = new TextView(activity);
        view.setText((okay ? "OK  " : "CHECK  ") + label + (okay ? "" : ": " + failureText));
        view.setTextColor(okay ? Color.rgb(46, 101, 52) : Color.rgb(145, 75, 24));
        view.setTextSize(12);
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        view.setBackgroundColor(Color.rgb(245, 245, 245));
        view.setLayoutParams(matchWrapMargins(0, 0, 0, dp(4)));
        return view;
    }

    private View titleSquare() {
        View view = new View(activity);
        view.setBackgroundResource(R.drawable.hedgeyos_mini_title_button);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(8), dp(8)));
        return view;
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private void positionFromPreferences() {
        if (hedgehog == null || content.getWidth() <= 0 || content.getHeight() <= 0 || dragging) {
            return;
        }
        int margin = dp(EDGE_MARGIN_DP);
        int maxX = Math.max(margin,
            content.getWidth() - hedgehog.getWidth() - margin);
        int maxY = Math.max(margin,
            content.getHeight() - hedgehog.getHeight() - margin);
        float defaultX = 1f;
        float defaultY = 0f;
        float normalizedX = preferences.getFloat(KEY_X, defaultX);
        float normalizedY = preferences.getFloat(KEY_Y, defaultY);
        int x = margin + Math.round(clamp(normalizedX, 0f, 1f) * (maxX - margin));
        int y = margin + Math.round(clamp(normalizedY, 0f, 1f) * (maxY - margin));
        setPosition(x, y);
        positioned = true;
    }

    private void setPosition(int x, int y) {
        if (hedgehog == null || content.getWidth() <= 0 || content.getHeight() <= 0) {
            return;
        }
        int margin = dp(EDGE_MARGIN_DP);
        int maxX = Math.max(margin,
            content.getWidth() - hedgehog.getWidth() - margin);
        int maxY = Math.max(margin,
            content.getHeight() - hedgehog.getHeight() - margin);
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) hedgehog.getLayoutParams();
        params.leftMargin = clamp(x, margin, maxX);
        params.topMargin = clamp(y, margin, maxY);
        hedgehog.setLayoutParams(params);
        hedgehog.bringToFront();
    }

    private void saveNormalizedPosition() {
        if (hedgehog == null || !positioned) {
            return;
        }
        int margin = dp(EDGE_MARGIN_DP);
        int maxX = Math.max(margin,
            content.getWidth() - hedgehog.getWidth() - margin);
        int maxY = Math.max(margin,
            content.getHeight() - hedgehog.getHeight() - margin);
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) hedgehog.getLayoutParams();
        float normalizedX = maxX == margin
            ? 0f
            : (params.leftMargin - margin) / (float) (maxX - margin);
        float normalizedY = maxY == margin
            ? 0f
            : (params.topMargin - margin) / (float) (maxY - margin);
        preferences.edit()
            .putFloat(KEY_X, clamp(normalizedX, 0f, 1f))
            .putFloat(KEY_Y, clamp(normalizedY, 0f, 1f))
            .apply();
    }

    private void dismissPopup() {
        PopupWindow current = popup;
        popup = null;
        if (current != null && current.isShowing()) {
            current.dismiss();
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
