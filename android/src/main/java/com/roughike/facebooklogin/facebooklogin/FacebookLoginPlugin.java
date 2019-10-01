package com.roughike.facebooklogin.facebooklogin;

import android.os.Bundle;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookSdk;
import com.facebook.LoggingBehavior;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginBehavior;
import com.facebook.login.LoginManager;

import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class FacebookLoginPlugin implements MethodCallHandler {
    private static final String CHANNEL_NAME = "com.roughike/flutter_facebook_login";

    private static final String ERROR_UNKNOWN_LOGIN_BEHAVIOR = "unknown_login_behavior";

    private static final String METHOD_LOG_IN = "logIn";
    private static final String METHOD_LOG_OUT = "logOut";
    private static final String METHOD_GET_CURRENT_ACCESS_TOKEN = "getCurrentAccessToken";
    private static final String METHOD_LOG_EVENT = "logEvent";
    private static final String METHOD_SET_USER_ID = "setUserId";
    private static final String METHOD_CLEAR_USER_ID = "clearUserId";
    private static final String METHOD_SET_DEBUG_MODE = "setDebugMode";

    private static final String ARG_LOGIN_BEHAVIOR = "behavior";
    private static final String ARG_PERMISSIONS = "permissions";
    private static final String ARG_EVENT_NAME = "name";
    private static final String ARG_EVENT_PARAMS = "params";
    private static final String ARG_USER_ID = "userId";
    private static final String ARG_IS_DEBUG_MODE = "isDebugMode";

    private static final String LOGIN_BEHAVIOR_NATIVE_WITH_FALLBACK = "nativeWithFallback";
    private static final String LOGIN_BEHAVIOR_NATIVE_ONLY = "nativeOnly";
    private static final String LOGIN_BEHAVIOR_WEB_ONLY = "webOnly";
    private static final String LOGIN_BEHAVIOR_WEB_VIEW_ONLY = "webViewOnly";

    private final FacebookSignInDelegate delegate;

    private FacebookLoginPlugin(Registrar registrar) {
        delegate = new FacebookSignInDelegate(registrar);
    }

    public static void registerWith(Registrar registrar) {
        final FacebookLoginPlugin plugin = new FacebookLoginPlugin(registrar);
        final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL_NAME);
        channel.setMethodCallHandler(plugin);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        String loginBehaviorStr;
        LoginBehavior loginBehavior;

        switch (call.method) {
            case METHOD_LOG_IN:
                loginBehaviorStr = call.argument(ARG_LOGIN_BEHAVIOR);
                loginBehavior = loginBehaviorFromString(loginBehaviorStr, result);
                List<String> permissions = call.argument(ARG_PERMISSIONS);

                delegate.logIn(loginBehavior, permissions, result);
                break;
            case METHOD_LOG_OUT:
                delegate.logOut(result);
                break;
            case METHOD_GET_CURRENT_ACCESS_TOKEN:
                delegate.getCurrentAccessToken(result);
                break;
            case METHOD_LOG_EVENT:
                String eventName = call.argument(ARG_EVENT_NAME);
                Map<String, Object> eventParams = call.argument(ARG_EVENT_PARAMS);
                delegate.logEvent(eventName, eventParams, result);
                break;
            case METHOD_SET_USER_ID:
                String userId = call.argument(ARG_USER_ID);
                delegate.setUserId(userId, result);
                break;
            case METHOD_CLEAR_USER_ID:
                delegate.clearUserId(result);
                break;
            case METHOD_SET_DEBUG_MODE:
                Boolean isDebugMode = call.argument(ARG_IS_DEBUG_MODE);
                delegate.setDebugMode(isDebugMode);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private LoginBehavior loginBehaviorFromString(String loginBehavior, Result result) {
        switch (loginBehavior) {
            case LOGIN_BEHAVIOR_NATIVE_WITH_FALLBACK:
                return LoginBehavior.NATIVE_WITH_FALLBACK;
            case LOGIN_BEHAVIOR_NATIVE_ONLY:
                return LoginBehavior.NATIVE_ONLY;
            case LOGIN_BEHAVIOR_WEB_ONLY:
                return LoginBehavior.WEB_ONLY;
            case LOGIN_BEHAVIOR_WEB_VIEW_ONLY:
                return LoginBehavior.WEB_VIEW_ONLY;
            default:
                result.error(
                        ERROR_UNKNOWN_LOGIN_BEHAVIOR,
                        "setLoginBehavior called with unknown login behavior: "
                                + loginBehavior,
                        null
                );
                return null;
        }
    }

    public static final class FacebookSignInDelegate {
        private final Registrar registrar;
        private final CallbackManager callbackManager;
        private final LoginManager loginManager;
        private final FacebookLoginResultDelegate resultDelegate;
        private final AppEventsLogger logger;

        public FacebookSignInDelegate(Registrar registrar) {
            this.registrar = registrar;
            this.callbackManager = CallbackManager.Factory.create();
            this.loginManager = LoginManager.getInstance();
            this.resultDelegate = new FacebookLoginResultDelegate(callbackManager);
            this.logger = AppEventsLogger.newLogger(registrar.context());

            loginManager.registerCallback(callbackManager, resultDelegate);
            registrar.addActivityResultListener(resultDelegate);
        }

        public void logIn(
                LoginBehavior loginBehavior, List<String> permissions, Result result) {
            resultDelegate.setPendingResult(METHOD_LOG_IN, result);

            loginManager.setLoginBehavior(loginBehavior);
            loginManager.logIn(registrar.activity(), permissions);
        }

        public void logOut(Result result) {
            loginManager.logOut();
            result.success(null);
        }

        public void getCurrentAccessToken(Result result) {
            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            Map<String, Object> tokenMap = FacebookLoginResults.accessToken(accessToken);

            result.success(tokenMap);
        }

        public void logEvent(String eventName, Map<String, Object> eventParams, Result result) {
            Bundle bundle = new Bundle();
            for (Map.Entry<String, Object> entry : eventParams.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    bundle.putString(entry.getKey(), (String) value);
                } else if (value instanceof Integer) {
                    bundle.putInt(entry.getKey(), (Integer) value);
                } else {
                    bundle.putString(entry.getKey(), value.toString());
                }
            }
            logger.logEvent(eventName, bundle);
            result.success(null);
        }

        public void setUserId(String userId, Result result) {
            AppEventsLogger.setUserID(userId);
            result.success(null);
        }

        public void clearUserId(Result result) {
            AppEventsLogger.clearUserID();
            result.success(null);
        }

        public void setDebugMode(Boolean isDebugMode) {
            FacebookSdk.setIsDebugEnabled(isDebugMode);
            if (isDebugMode) {
                FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS);
            } else {
                FacebookSdk.removeLoggingBehavior(LoggingBehavior.APP_EVENTS);
            }
        }
    }
}
