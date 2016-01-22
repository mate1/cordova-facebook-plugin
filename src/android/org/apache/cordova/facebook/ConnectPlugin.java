package org.apache.cordova.facebook;

import java.io.UnsupportedEncodingException;
import java.lang.System;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookDialogException;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookSdk;
import com.facebook.FacebookServiceException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.share.Sharer;
import com.facebook.share.model.AppGroupCreationContent;
import com.facebook.share.model.AppInviteContent;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.model.ShareVideo;
import com.facebook.share.model.ShareVideoContent;
import com.facebook.share.widget.AppInviteDialog;
import com.facebook.share.widget.CreateAppGroupDialog;
import com.facebook.share.widget.GameRequestDialog;
import com.facebook.share.widget.JoinAppGroupDialog;
import com.facebook.share.widget.ShareDialog;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class ConnectPlugin extends CordovaPlugin {

    private static final int INVALID_ERROR_CODE = -2; //-1 is FacebookRequestError.INVALID_ERROR_CODE
    private static final String PUBLISH_PERMISSION_PREFIX = "publish";
    private static final String MANAGE_PERMISSION_PREFIX = "manage";
    @SuppressWarnings("serial")
    private static final Set<String> OTHER_PUBLISH_PERMISSIONS = new HashSet<String>() {
        {
            add("ads_management");
            add("create_event");
            add("rsvp_event");
        }
    };
    private static final String TAG = "ConnectPlugin";

    private CreateAppGroupDialog addGroupDialog;
    private AppInviteDialog appInviteDialog;
    private CallbackManager callbackManager;
    private CallbackContext dialogContext;
    private JSONObject dialogParams;
    private GameRequestDialog gameRequestDialog;
    private JoinAppGroupDialog joinGroupDialog;
    private AppEventsLogger logger;
    private CallbackContext loginContext;
    private CallbackContext graphContext;
    private String graphPath;
    private ShareDialog shareDialog;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        // Init logger
        logger = AppEventsLogger.newLogger(cordova.getActivity());

        // Set up the activity result callback to this class
        callbackManager = CallbackManager.Factory.create();
        cordova.setActivityResultCallback(this);

        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                if (loginContext != null)
                    // Initial login, retrieve user information
                    GraphRequest.newMeRequest(AccessToken.getCurrentAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                        @Override
                        public void onCompleted(JSONObject jsonObject, GraphResponse response) {
                            // Create a new result with response data
                            if (loginContext != null) {
                                Log.d(TAG, "Returning login object " + jsonObject.toString());
                                sendAccessToken(loginContext);
                                loginContext = null;
                            }
                        }
                    }).executeAsync();
                else if (graphContext != null)
                    // Requested extra permissions, execute pending Graph API call
                    makeGraphCall();
            }

            @Override
            public void onCancel() {
                handleError(new FacebookAuthorizationException("Used canceled login dialog"), loginContext);
            }

            @Override
            public void onError(FacebookException exception) {
                // only handle FacebookOperationCanceledException to support
                // SDK recovery behavior triggered by
                Log.e(TAG, "Exception:" + exception.toString());
                handleError(exception, loginContext);
            }
        });

        // Setup add group dialog
        addGroupDialog = new CreateAppGroupDialog(cordova.getActivity());
        addGroupDialog.registerCallback(callbackManager, new FacebookCallback<CreateAppGroupDialog.Result>()
        {
            @Override
            public void onSuccess(CreateAppGroupDialog.Result result)
            {
                Bundle data = new Bundle();
                data.putString("group_id", result.getId());
                handleSuccess(data, dialogContext);
            }

            @Override
            public void onCancel()
            {
                handleError(new FacebookException("Used canceled add group dialog"), dialogContext);
            }

            @Override
            public void onError(FacebookException e)
            {
                handleError(e, dialogContext);
            }
        });

        // Setup app invite dialog
        appInviteDialog = new AppInviteDialog(cordova.getActivity());
        appInviteDialog.registerCallback(callbackManager, new FacebookCallback<AppInviteDialog.Result>()
        {
            @Override
            public void onSuccess(AppInviteDialog.Result result)
            {
                handleSuccess(result.getData(), dialogContext);
            }

            @Override
            public void onCancel()
            {
                handleError(new FacebookException("Used canceled app invite dialog"), dialogContext);
            }

            @Override
            public void onError(FacebookException e)
            {
                handleError(e, dialogContext);
            }
        });

        // Setup game request dialog
        gameRequestDialog = new GameRequestDialog(cordova.getActivity());
        gameRequestDialog.registerCallback(callbackManager, new FacebookCallback<GameRequestDialog.Result>()
        {
            @Override
            public void onSuccess(GameRequestDialog.Result result)
            {
                Bundle data = new Bundle();
                data.putString("request_id", result.getRequestId());
                handleSuccess(data, dialogContext);
            }

            @Override
            public void onCancel()
            {
                handleError(new FacebookException("Used canceled game request dialog"), dialogContext);
            }

            @Override
            public void onError(FacebookException e)
            {
                handleError(e, dialogContext);
            }
        });

        // Setup join group dialog
        joinGroupDialog = new JoinAppGroupDialog(cordova.getActivity());
        joinGroupDialog.registerCallback(callbackManager, new FacebookCallback<JoinAppGroupDialog.Result>()
        {
            @Override
            public void onSuccess(JoinAppGroupDialog.Result result)
            {
                handleSuccess(result.getData(), dialogContext);
            }

            @Override
            public void onCancel()
            {
                handleError(new FacebookException("Used canceled join group dialog"), dialogContext);
            }

            @Override
            public void onError(FacebookException e)
            {
                handleError(e, dialogContext);
            }
        });

        // Setup sharing dialog
        shareDialog = new ShareDialog(cordova.getActivity());
        shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>()
        {
            @Override
            public void onSuccess(Sharer.Result result)
            {
                Bundle data = new Bundle();
                data.putString("post_id", result.getPostId());
                handleSuccess(data, dialogContext);
            }

            @Override
            public void onCancel()
            {
                handleError(new FacebookException("Used canceled sharing dialog"), dialogContext);
            }

            @Override
            public void onError(FacebookException e)
            {
                handleError(e, dialogContext);
            }
        });


        super.initialize(cordova, webView);
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        // Developers can observe how frequently users activate their app by logging an app activation event.
        AppEventsLogger.activateApp(cordova.getActivity());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Log.d(TAG, "activity result in plugin: requestCode(" + requestCode + "), resultCode(" + resultCode + ")");
        callbackManager.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equalsIgnoreCase("login"))
        {
            Log.d(TAG, "login FB");

            // Get the permissions
            String[] arrayPermissions = new String[args.length()];
            for (int i = 0; i < args.length(); i++)
                arrayPermissions[i] = args.getString(i);

            // Set a pending callback to Cordova
            loginContext = callbackContext;
            PluginResult pr = new PluginResult(PluginResult.Status.NO_RESULT);
            pr.setKeepCallback(true);
            loginContext.sendPluginResult(pr);

            // Perform login
            login(Arrays.asList(arrayPermissions), loginContext);

            return true;
        }
        else if (action.equalsIgnoreCase("logout"))
        {
            // Perform logout
            LoginManager.getInstance().logOut();
            callbackContext.success();
            return true;
        }
        else if (action.equalsIgnoreCase("getAccessToken"))
        {
            sendAccessToken(callbackContext);
            return true;
        }
        else if (action.equalsIgnoreCase("getApplicationSignature"))
        {
            String sig = FacebookSdk.getApplicationSignature(webView.getContext());
            if (sig != null)
            {
                // strip the linefeed...
                sig = sig.replace(System.lineSeparator(), "");
                // ... and pad the result with ='s because it needs to be 28 bytes per Fb's requirements
                while (sig.length() < 28)
                    sig += "=";
                Log.w(TAG, "getApplicationSignature result: " + sig);
                callbackContext.success(sig);
            }
            else
                callbackContext.error("Could not determine signature.");
            return true;
        }
        else if (action.equalsIgnoreCase("logEvent"))
        {
            if (args.length() == 0) {
                // Not enough parameters
                callbackContext.error("Invalid arguments");
                return true;
            }
            String eventName = args.getString(0);
            if (args.length() == 1)
                logger.logEvent(eventName);
            else {
                // Arguments is greater than 1
                JSONObject params = args.getJSONObject(1);
                Bundle parameters = new Bundle();

                Iterator<String> iterator = params.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    try {
                        // Try get a String
                        String value = params.getString(key);
                        parameters.putString(key, value);
                    }
                    catch (Exception e) {
                        // Maybe it was an int
                        try {
                            int value = params.getInt(key);
                            parameters.putInt(key, value);
                            Log.w(TAG, "Type in AppEvent parameters was not String for key: " + key);
                        }
                        catch (Exception e2) {
                            // Nope
                            Log.e(TAG, "Unsupported type in AppEvent parameters for key: " + key);
                        }
                    }
                }
                if (args.length() == 2)
                    logger.logEvent(eventName, parameters);
                if (args.length() == 3)
                    logger.logEvent(eventName, args.getDouble(2), parameters);
            }
            callbackContext.success();
            return true;
        }
        else if (action.equalsIgnoreCase("logPurchase"))
        {
            if (args.length() != 2)
            {
                callbackContext.error("Invalid arguments");
                return true;
            }
            logger.logPurchase(BigDecimal.valueOf(args.getInt(0)), Currency.getInstance(args.getString(1)));
            callbackContext.success();
            return true;
        }
        else if (action.equalsIgnoreCase("showDialog"))
        {
            String method;
            try {
                dialogParams = args.getJSONObject(0);
                method = (String)dialogParams.remove("method");
            }
            catch (JSONException e) {
                Log.e(TAG, "Exception:" + e.toString(), e);
                method = null;
            }

            if (method == null || method.trim().length() == 0) {
                handleError(new FacebookException("Missing or invalid dialog method."), dialogContext);
                return true;
            }

            // Begin by sending a callback pending notice to Cordova
            dialogContext = callbackContext;
            PluginResult pr = new PluginResult(PluginResult.Status.NO_RESULT);
            pr.setKeepCallback(true);
            dialogContext.sendPluginResult(pr);

            if (method.equalsIgnoreCase("add_group"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            AppGroupCreationContent.Builder builder = new AppGroupCreationContent.Builder();
                            builder.setAppGroupPrivacy(AppGroupCreationContent.AppGroupPrivacy.valueOf(dialogParams.getString("privacy")));
                            builder.setDescription(dialogParams.getString("description"));
                            builder.setName(dialogParams.getString("name"));
                            addGroupDialog.show(builder.build());
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else if (method.equalsIgnoreCase("app_invite"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            AppInviteContent.Builder builder = new AppInviteContent.Builder();
                            builder.setApplinkUrl(dialogParams.getString("app_url"));
                            builder.setPreviewImageUrl(dialogParams.getString("preview_url"));
                            appInviteDialog.show(builder.build());
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else if (method.equalsIgnoreCase("game_request"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            GameRequestContent.Builder builder = new GameRequestContent.Builder();
                            builder.setActionType(GameRequestContent.ActionType.valueOf(dialogParams.getString("action_type")));
                            builder.setData(dialogParams.getString("data"));
                            builder.setFilters(GameRequestContent.Filters.valueOf(dialogParams.getString("filters")));
                            builder.setMessage(dialogParams.getString("message"));
                            builder.setObjectId(dialogParams.getString("object_id"));
                            builder.setSuggestions(new ArrayList<String>(Arrays.asList(dialogParams.getString("suggested_recipient_ids").split(","))));
                            builder.setTitle(dialogParams.getString("title"));
                            builder.setTo(dialogParams.getString("recipient_ids").split(",")[0]);
                            gameRequestDialog.show(builder.build());
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else if (method.equalsIgnoreCase("join_group"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            joinGroupDialog.show(dialogParams.getString("group_id"));
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else if (method.equalsIgnoreCase("share_link"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            // Publish the post using the Share Dialog
                            ShareLinkContent.Builder builder = new ShareLinkContent.Builder();
                            builder.setContentDescription(dialogParams.getString("description"));
                            builder.setContentTitle(dialogParams.getString("title"));
                            builder.setImageUrl(Uri.parse(dialogParams.getString("image_url")));
                            builder.setContentUrl(Uri.parse(dialogParams.getString("url")));
                            builder.setPeopleIds(Arrays.asList(dialogParams.getString("people_ids").split(",")));
                            builder.setPlaceId(dialogParams.getString("place_id"));
                            builder.setRef(dialogParams.getString("reference"));

                            shareDialog.show(builder.build());
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else if (method.equalsIgnoreCase("share_photo"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            List<SharePhoto> photos = new ArrayList<SharePhoto>();
                            JSONArray photoData = dialogParams.getJSONArray("photos");
                            SharePhoto.Builder photoBuilder = new SharePhoto.Builder();
                            for (int i = 0; i < photoData.length(); i++) {
                                //builder.setBitmap(photos.getJSONObject(i).getString("image"));
                                photoBuilder.setCaption(photoData.getJSONObject(i).getString("caption"));
                                photoBuilder.setImageUrl(Uri.parse(photoData.getJSONObject(i).getString("image_url")));
                                photoBuilder.setUserGenerated(photoData.getJSONObject(i).getBoolean("user_generated"));
                                photos.add(photoBuilder.build());
                            }

                            // Publish the post using the Share Dialog
                            SharePhotoContent.Builder builder = new SharePhotoContent.Builder();
                            builder.setPhotos(photos);
                            builder.setContentUrl(Uri.parse(dialogParams.getString("url")));
                            builder.setPeopleIds(Arrays.asList(dialogParams.getString("people_ids").split(",")));
                            builder.setPlaceId(dialogParams.getString("place_id"));
                            builder.setRef(dialogParams.getString("reference"));

                            shareDialog.show(builder.build());
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else if (method.equalsIgnoreCase("share_video"))
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject photoData = dialogParams.getJSONObject("preview_photo");
                            SharePhoto.Builder photoBuilder = new SharePhoto.Builder();
                            //builder.setBitmap(photos.getString("image")); TODO is this required or optional?
                            photoBuilder.setCaption(photoData.getString("caption"));
                            photoBuilder.setImageUrl(Uri.parse(photoData.getString("image_url")));
                            photoBuilder.setUserGenerated(photoData.getBoolean("user_generated"));

                            ShareVideo.Builder videoBuilder = new ShareVideo.Builder();
                            videoBuilder.setLocalUrl(Uri.parse(dialogParams.getString("video_url")));

                            // Publish the post using the Share Dialog
                            ShareVideoContent.Builder builder = new ShareVideoContent.Builder();
                            builder.setContentDescription(dialogParams.getString("description"));
                            builder.setContentTitle(dialogParams.getString("title"));
                            builder.setPreviewPhoto(photoBuilder.build());
                            builder.setVideo(videoBuilder.build());
                            builder.setContentUrl(Uri.parse(dialogParams.getString("url")));
                            builder.setPeopleIds(Arrays.asList(dialogParams.getString("people_ids").split(",")));
                            builder.setPlaceId(dialogParams.getString("place_id"));
                            builder.setRef(dialogParams.getString("reference"));

                            shareDialog.show(builder.build());
                        }
                        catch (Exception e) {
                            handleError(e, dialogContext);
                        }
                    }
                });
            else
                dialogContext.error("Unsupported dialog method.");

            return true;
        }
        else if (action.equalsIgnoreCase("graphApi"))
        {
            graphContext = callbackContext;
            PluginResult pr = new PluginResult(PluginResult.Status.NO_RESULT);
            pr.setKeepCallback(true);
            graphContext.sendPluginResult(pr);
            graphPath = args.getString(0);

            JSONArray arr = args.getJSONArray(1);
            final List<String> permissionsList = new ArrayList<String>();
            for (int i = 0; i < arr.length(); i++)
                permissionsList.add(arr.getString(i));

            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            if (permissionsList.size() == 0 || (accessToken != null && accessToken.getPermissions().containsAll(permissionsList)))
                // Perform call to Graph API
                makeGraphCall();
            else
                // Request extra permissions before executing call to Graph API
                login(permissionsList, graphContext);
            return true;
        }
        return false;
    }

    private void handleError(Exception exception, CallbackContext context) {
        String errMsg = "Facebook error: " + exception.getMessage();
        int errorCode = INVALID_ERROR_CODE;
        // User clicked "x"
        if (exception instanceof FacebookOperationCanceledException) {
            errMsg = "User cancelled dialog";
            errorCode = 4201;
        }
        else if (exception instanceof FacebookDialogException) {
            // Dialog error
            errMsg = "Dialog error: " + exception.getMessage();
        }

        Log.e(TAG, exception.toString(), exception);
        context.error(getErrorResponse(exception, errMsg, errorCode));
    }

    private void handleSuccess(Bundle values, CallbackContext callbackContext) {
        // Handle a successful dialog:
        // Send the URL parameters back, for a requests dialog, the "request" parameter
        // will include the resulting request id. For a feed dialog, the "post_id"
        // parameter will include the resulting post id.
        JSONObject response = new JSONObject();
        try {
            for (String key : values.keySet()) {
                //check if key is array
                int index = key.indexOf("[");
                if (index >= 0) {
                    String normalizedKey = key.substring(0, index);
                    if (!response.has(normalizedKey))
                        response.put(normalizedKey, new JSONArray());
                    JSONArray result = (JSONArray) response.get(normalizedKey);
                    result.put(result.length(), values.get(key));
                }
                else
                    response.put(key, values.get(key));
            }
        }
        catch (JSONException e) {
            Log.e(TAG, "Exception:" + e.toString(), e);
        }
        callbackContext.success(response);
    }

    private void login(List<String> permissionsList, CallbackContext callbackContext) {
        boolean publish = false;
        boolean read = false;

        // Check whether we have read or write permissions
        for (String permission : permissionsList) {
            if (permission != null) {
                if (permission.startsWith(PUBLISH_PERMISSION_PREFIX) || permission.startsWith(MANAGE_PERMISSION_PREFIX) || OTHER_PUBLISH_PERMISSIONS.contains(permission))
                    publish = true;
                else
                    read = true;
                // Break if we have a mixed bag, as this is an error
                if (publish && read) {
                    callbackContext.error("Cannot request both publish and read permissions at the same time.");
                    return;
                }
            }
        }

        // Reset the activity result callback to cancel any previously pending activity.
        cordova.setActivityResultCallback(this);

        // Perform login with list of permissions
        if (publish)
            LoginManager.getInstance().logInWithPublishPermissions(cordova.getActivity(), permissionsList);
        else
            LoginManager.getInstance().logInWithReadPermissions(cordova.getActivity(), permissionsList);
    }

    private void makeGraphCall() {
        //If you're using the paging URLs they will be URLEncoded, let's decode them.
        try {
            graphPath = URLDecoder.decode(graphPath, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Exception:" + e.toString(), e);
        }
        String[] urlParts = graphPath.split("\\?");

        Bundle params = new Bundle();
        if (urlParts.length > 1) {
            for (String query : urlParts[1].split("&")) {
                int index = query.indexOf("=");
                if (index > 0) {
                    String key = query.substring(0, index);
                    String value = query.substring(index + 1, query.length());
                    params.putString(key, value);
                }
            }
        }

        GraphRequest graphRequest = new GraphRequest(AccessToken.getCurrentAccessToken(), urlParts[0], params, null, new GraphRequest.Callback() {
            @Override
            public void onCompleted(GraphResponse response) {
                if (graphContext != null) {
                    if (response.getError() != null)
                        graphContext.error(getFacebookRequestErrorResponse(response.getError()));
                    else
                        graphContext.success(response.getJSONObject());
                    graphPath = null;
                    graphContext = null;
                }
            }
        });
        graphRequest.executeAsync();
    }

    /**
     * Responds to the specified callback with information on the current Access Token, if a valid one exists, otherwise returns and error.
     */
    private void sendAccessToken(CallbackContext callbackContext) {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken != null && !accessToken.isExpired()) {
            try {
                JSONObject response = new JSONObject("{"
                    + "\"accessToken\": \"" + accessToken.getToken() + "\","
                    + "\"declinedPermissions\": " + new JSONArray(accessToken.getDeclinedPermissions()) + ","
                    + "\"expiresIn\": " + Math.max((accessToken.getExpires().getTime() - System.currentTimeMillis()) / 1000L, 0) + ","
                    + "\"lastRefresh\": " + accessToken.getLastRefresh().getTime() + ","
                    + "\"permissions\": " + new JSONArray(accessToken.getPermissions()) + ","
                    + "\"userID\": \"" + accessToken.getUserId() + "\""
                    + "}"
                );
                callbackContext.success(response);
            }
            catch (JSONException e) {
                Log.e(TAG, "Exception:" + e.toString(), e);
                callbackContext.error("No valid access token found.");
            }
        }
        else
            callbackContext.error("No valid access token found.");
    }

    private JSONObject getFacebookRequestErrorResponse(FacebookRequestError error) {
        try {
            return new JSONObject("{"
                    + "\"errorCode\": \"" + error.getErrorCode() + "\","
                    + "\"errorType\": \"" + error.getErrorType() + "\","
                    + "\"errorMessage\": \"" + error.getErrorMessage() + "\","
                    + "\"errorUserMessage\": \"" + error.getErrorUserMessage() + "\""
                    + "}");
        }
        catch (JSONException e) {
            Log.e(TAG, "Exception:" + e.toString(), e);
        }
        return new JSONObject();
    }

    private JSONObject getErrorResponse(Exception error, String message, int errorCode) {
        if (error instanceof FacebookServiceException)
            return getFacebookRequestErrorResponse(((FacebookServiceException) error).getRequestError());

        String response = "{";

        if (error instanceof FacebookDialogException)
            errorCode = ((FacebookDialogException) error).getErrorCode();

        if (errorCode != INVALID_ERROR_CODE)
            response += "\"errorCode\": \"" + errorCode + "\",";

        if (message == null)
            message = error.getMessage();

        response += "\"errorMessage\": \"" + message + "\"}";

        try {
            return new JSONObject(response);
        }
        catch (JSONException e) {
            Log.e(TAG, "Exception:" + e.toString(), e);
        }
        return new JSONObject();
    }
}
