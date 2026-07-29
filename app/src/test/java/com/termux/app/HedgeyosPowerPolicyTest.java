package com.termux.app;

import android.content.Context;
import android.os.Build;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
public class HedgeyosPowerPolicyTest {

    private Context context;
    private String originalManufacturer;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("hedgeyos_power_setup", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit();
        originalManufacturer = Build.MANUFACTURER;
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(Build.class, "MANUFACTURER", originalManufacturer);
    }

    @Test
    public void onboardingRequiresExplicitCompletionAndPersistsManualReview() {
        Assert.assertFalse(HedgeyosPowerPolicy.isOnboardingComplete(context));
        Assert.assertFalse(HedgeyosPowerPolicy.wasManualBackgroundSetupReviewed(context));

        HedgeyosPowerPolicy.completeOnboarding(context, true);

        Assert.assertTrue(HedgeyosPowerPolicy.isOnboardingComplete(context));
        Assert.assertTrue(HedgeyosPowerPolicy.wasManualBackgroundSetupReviewed(context));
    }

    @Test
    public void colorOsGuidanceCoversBackgroundAndAutostartControls() {
        ReflectionHelpers.setStaticField(Build.class, "MANUFACTURER", "OPPO");

        String guidance = HedgeyosPowerPolicy.vendorInstructions();

        Assert.assertTrue(guidance.contains("Unrestricted"));
        Assert.assertTrue(guidance.contains("background activity"));
        Assert.assertTrue(guidance.contains("Auto launch"));
        Assert.assertEquals("OPPO", HedgeyosPowerPolicy.manufacturerLabel());
    }
}
