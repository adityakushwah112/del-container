package com.del.delcontainer.utils;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.del.delcontainer.database.entities.Heart;
import com.del.delcontainer.managers.DataManager;
import com.del.delcontainer.managers.DelAppManager;
import com.del.delcontainer.repositories.HeartRateRepository;
import com.del.delcontainer.services.LocationService;
import com.del.delcontainer.services.SensorsService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * App APIs exposed to the DEL web applications
 * Just a toast API for now - expand to include
 * app registration and data transfer,
 */
public class DELUtils {

    private Context context;
    private static DELUtils delUtils = null;
    private static final String TAG = "DELUtils";

    private DELUtils() {
    }

    public void setContext(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DELUtils getInstance() {
        if (null == delUtils) {
            delUtils = new DELUtils();
        }

        return delUtils;
    }

    /**
     * Build a function call formatted for the appView as a string
     * Eg: "function_name('param1', 'param2')"
     *
     * @param targetFunction
     * @param params
     * @return
     */
    public String getTargetFunctionString(final String targetFunction, final Object[] params) {

        // Build a function call with parameters as a string
        StringBuilder sb = new StringBuilder(targetFunction).append('(');
        for (int i = 0; i < params.length; i++) {
            if(params[i] instanceof String) {
                sb.append("\'").append(params[i]).append("\'");
            }

            if(i != params.length - 1) {
                sb.append(",");
            }
        }

        sb.append(')');
        return sb.toString();
    }

    /**
     * Function to execute DelApp function
     *
     * @param appView
     * @param targetFunction
     */
    public void callDelAppFunction(final WebView appView, final String targetFunction) {

        if (null == appView) {
            Log.d(TAG, "callDelAppFunction: Invalid service reference. App does not exist");
            return;
        }

        // Pass in a thread instance to perform action
        appView.post(() -> {
            // Versions above API level 19 (KitKat) support evaluateJavascript
            // the ones below support loadUrl
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                appView.loadUrl("javascript:" + targetFunction);
            } else {
                // result -> string returned to onReceiveValue
                appView.evaluateJavascript(targetFunction, (result) -> {

                    Log.d(TAG, "callDelAppFunction: Returned : " + result);
                });
            }
        });
    }

    /**
     * Inject the registered app ID and app name to the service
     * when launched. This will be used by the services when terminating
     * @param appId
     * @param appName
     */
    public void setAppIdAndName(String appId, String appName) {

    }

    @JavascriptInterface
    public void makeToast(String message) {
        Log.d(TAG, "makeToast: Called MakeToast");
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void registerApp(String appDetails) {

        UUID appId = null;
        String appName = null;

        try {
            JSONObject appInfo = new JSONObject(appDetails);
            appId = UUID.fromString(appInfo.getString(Constants.APP_ID));
            appName = appInfo.getString(Constants.APP_NAME);

            Log.d(TAG, "registerApp: " + appName + "; ID : " + appId);

        } catch (JSONException e) {
            Log.d(TAG, "registerApp: ");
        }

        // Broadcast app registration
        Intent intent = new Intent(Constants.EVENT_APP_REGISTERED);
        intent.putExtra(Constants.APP_ID, appId);
        intent.putExtra(Constants.APP_NAME, appName);

        Log.d(TAG, "registerApp: sending broadcast");
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @JavascriptInterface
    public void setContainerRequest(String appDetails) {

        Log.d(TAG, "setContainerRequest: setting container request");
        String appId = null;
        String request = null;

        try {
            JSONObject reqObject = new JSONObject(appDetails);
            appId = reqObject.getString(Constants.APP_ID);
            request = reqObject.getString("request");

            // Register request in data manager
            DataManager dataManager = DataManager.getInstance();
            ArrayList<String> requests = dataManager.getDataRequestMap().get(appId);

            if(null == requests) {
                requests = new ArrayList<>();
                requests.add(request);
            } else {
                requests.add(request);
            }
            dataManager.getDataRequestMap().put(appId, requests);

        } catch(Exception e) {
            Log.d(TAG, "setContainerRequest : " + e.getMessage());
        }
    }

    @JavascriptInterface
    public String getLatestHeartData() {

        JSONObject hrData = new JSONObject();

        Heart heartData = HeartRateRepository.getInstance(context.getApplicationContext())
                .getLatestHeartData();
        if (null == heartData) {
            return "No Data";
        }

        try {
            hrData.put("timestamp", heartData.getDate());
            hrData.put("heartRateValue", heartData.getHeartRate());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return hrData.toString();
    }

    @JavascriptInterface
    public String getAllHeartRate() {

        JSONArray hrDataArray = new JSONArray();
        JSONObject hrBlock = new JSONObject();

        List<Heart> heartRateData = HeartRateRepository.getInstance(
                context.getApplicationContext()).getHeartData();

        if (null == heartRateData) {
            return "No Data";
        }

        try {
            for (Heart heart : heartRateData) {
                JSONObject heartRateJsonData = new JSONObject();
                heartRateJsonData.put("timestamp", heart.getDate());
                heartRateJsonData.put("heartRateValue", heart.getHeartRate());
                hrDataArray.put(heartRateJsonData);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            hrBlock.put("hrData", hrDataArray);
        } catch (Exception e) {
            ;
        }

        return hrBlock.toString();
    }

    @JavascriptInterface
    public String getStepsCount() {
        return String.valueOf(SensorsService.getInstance().getStepCount());
    }

    @JavascriptInterface
    public String getCurrentLocation() {

        JSONObject locationObject = new JSONObject();
        LocationService locationService = LocationService.getInstance();
        locationService.setLocationServiceEnabled(true);

        Location location = locationService.getLastLocation();

        if (null != location) {
            try {
                locationObject.put("latitude", location.getLatitude());
                locationObject.put("longitude", location.getLongitude());
                locationObject.put("accuracy", location.getAccuracy());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return locationObject.toString();
    }

    // TODO: If there are more than one apps requesting the location, this fails
    @JavascriptInterface
    public void stopLocationUpdates() {
        Log.d(TAG, "stopLocationUpdates: Terminating location updates");
        LocationService locationService = LocationService.getInstance();
        locationService.setLocationServiceEnabled(false);
        locationService.stopLocationUpdates();
    }

    // Need to add repository interface in each function here
    @JavascriptInterface
    public String getLatestWeight() {
        return "";
    }

    @JavascriptInterface
    public String getBodyMassParams() {
        return "";
    }

    @JavascriptInterface
    public String getLatestStepCount() {
        return "";
    }

    @JavascriptInterface
    public String getStepGoals() {
        return "";
    }
}
