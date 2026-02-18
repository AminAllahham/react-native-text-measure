package com.reactlibrary;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ReactNativeTextMeasurePackage
 *
 * Registers ReactNativeTextMeasureModule with the React Native bridge.
 *
 * Add to your MainApplication.java:
 *
 *   @Override
 *   protected List<ReactPackage> getPackages() {
 *     return Arrays.<ReactPackage>asList(
 *       new MainReactPackage(),
 *       new ReactNativeTextMeasurePackage()   // ← add this line
 *     );
 *   }
 *
 * If you are on RN >= 0.60 with auto-linking enabled, you do NOT need to
 * manually add the package — it will be registered automatically via
 * react-native.config.js.
 */
public class ReactNativeTextMeasurePackage implements ReactPackage {

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        return Arrays.<NativeModule>asList(
            new ReactNativeTextMeasureModule(reactContext)
        );
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}