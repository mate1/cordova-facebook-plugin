# Apache Cordova Facebook SDK 4 Plugin

This is the official plugin for the version 4 of the Facebook SDK in Apache Cordova!

The Facebook plugin for [Apache Cordova](http://incubator.apache.org/cordova/) allows you to use the same
JavaScript code in your Cordova application as you use in your web application. However, unlike in the
browser, the Cordova application will use the native Facebook app to perform the operations.

* Supports Cordova CLI v6.x.x.
* Uses Facebook Android &amp; iOS SDK 4.5.1

This plugin was tested on Cordova Android v5.1.1.

For more information see the [Troubleshooting Guide/FAQ](TROUBLESHOOTING.md).

## Set-up

### Pre-requisite

To use this plugin you will first need an `APP_ID`, you can get one by registering your app with Facebook,
see [https://developers.facebook.com/apps](https://developers.facebook.com/apps) for more details.

Once you have your `APP_ID` you will need to add it to your app's resources in before you will be able
to use the SDK.

For **Android**, add the following values to your strings resource file (`res/values/strings.xml`):

```
<string name="app_name">Your application's name</string>
<string name="fb_app_id">...</string>
```

For **iOS**, add the following to your app's plist:

```
<key>FacebookAppID</key>
<string>...</string>
<key>FacebookDisplayName</key>
<string>${PRODUCT_NAME}</string>
```

### Installation

To install this plugin inside your app, simply execute the following command from within your app's
`cordova` directory:

`cordova plugin add https://github.com/mate1/cordova-facebook-plugin.git`

Then you will need to add calls to initialize Facebook's SDK within your app.

For **Android**, add the following call in your CordovaActivity's onCreate function:
```
// Initialize Facebook SDK
FacebookSdk.sdkInitialize(getApplicationContext());
```

For **iOS**, add the following in your AppDelegate's didFinishLaunchingWithOptions function:
```
// Facebook SDK
[[FBSDKApplicationDelegate sharedInstance] application:application didFinishLaunchingWithOptions:launchOptions];

```
and inside the openURL function:
```
// Forward to Facebook SDK
if ([[FBSDKApplicationDelegate sharedInstance] application:application openURL:url sourceApplication:sourceApplication annotation:annotation])
    return YES;
```

For the **Web**, add the following somewhere in your `index.js`:
```
<div id="fb-root"></div>
```
along with the following call within your app's initialization process:
```
if (window.cordova.platformId == "browser") {
    facebookConnectPlugin.browserInit(String appId, String apiVersion);
}
````

## API


### login()

`facebookConnectPlugin.login(String[] permissions, Function success, Function failure)`

This method allows you to create a new access token for the currently connect Facebook user, if there
is no active Facebook session a dialog will be shown to the user asking him to log into his Facebook
account.

If successful, the success function will be called and passed the following Object:
```
{
    accessToken: "...",
    declinedPermissions: [...],
    expiresIn: 1234567,
    lastRefresh: 12345678910,
    permissions: [...],
    userID: "123456789"
}
```
If any error occurs the failure function will be called and passed an error message.

**Sample**

```
facebookConnectPlugin.login(["public_profile"],
    function (accessToken) {
        console.log(accessToken);
    },
    function (error) {
        alert(error)
    }
);
```

### logout()

`facebookConnectPlugin.logout(Function success, Function failure)`

This function allows you to manually expire the currently active access token.

If successful, the success function will be called without any arguments.

If any error occurs the failure function will be called and passed an error message.

**Sample**

```
facebookConnectPlugin.logout(
    function (accessToken) {
        console.log("Logged out!");
    },
    function (error) {
        alert(error)
    }
);
```

### getAccessToken()

`facebookConnectPlugin.getAccessToken(Function success, Function failure)`

This functions allows you to recover the currently active access token, if the current access token
has expire the function will fail.

If successful, the success function will be called and passed the following Object:
```
{
    accessToken: "...",
    declinedPermissions: [...],
    expiresIn: 1234567,
    lastRefresh: 12345678910,
    permissions: [...],
    userID: "123456789"
}
```
If any error occurs the failure function will be called and passed an error message.

**Sample**

```
facebookConnectPlugin.getAccessToken(
    function (accessToken) {
        console.log(accessToken);
    },
    function (error) {
        alert(error)
    }
);
```

### api()

`facebookConnectPlugin.api(String requestPath, Array permissions, Function success, Function failure)`

If successful, the success function will be called and the Object returned by the Graph API.

If any error occurs the failure function will be called and passed an error message.

For more information see the [Graph API documentation](https://developers.facebook.com/docs/graph-api),
you can also test it using the [Graph API Explorer](https://developers.facebook.com/tools/explorer).

**Sample**

```
facebookConnectPlugin.api("<user-id>/?fields=id,email,birthday", ["user_birthday"],
    function (data) {
        console.log(data);
    },
    function (error) {
        alert(error);
    }
);
```

### showDialog()

`facebookConnectPlugin.showDialog(Object options, Function success, Function failure)`

This functions allows you to show different dialogs to the users, each dialog allowing the user to take
a certain action within his Facebook account. The passed options must contain a `method` value which
defines which type of dialog to show in addition to other arguments related to each dialog type.

If successful, the success function will be called and passed an Object containing results specific to
each dialog type.

If any error occurs the failure function will be called and passed an error message.

**Sample**

```
facebookConnectPlugin.showDialog({
        method: "share_link",
        description: "...",
        title: "...",
        url: "..."
    },
    function (result) { console.log(result) },
    function (error) { alert(error) }
);
```

#### Dialog types

**Add a group:**
```
{
    method: "add_group",
    privacy: "...",
    description: "...",
    name: "..."
}
```

**Send an app invite:**
```
{
    method: "app_invite",
    app_url: "...",
    preview_url: "..."
}
```

**Send a game request:**
```
{
    method: "game_request",
    action_type: "...",
    data: "...",
    filters: "...",
    message: "...",
    object_id: "...",
    suggested_recipient_ids: "...",
    title: "...",
    recipient_ids: "..."
}
```

**Join a group:**
```
{
    method: "join_group",
    group_id: "..."
}
```

**Share a link:**
```
{
    method: "share_link",
    description: "...",
    title: "...",
    image_url: "...",
    url: "...",
    people_ids: "...",
    place_id: "...",
    reference: "..."
}
```

**Share a photo:**
```
{
    method: "share_photo",
    people_ids: "...",
    place_id: "...",
    photos: [{
        caption: "...",
        image_url: "...",
        user_generated: "..."
    }, ...],
    reference: "...",
    url: "...",
}
```

**Share a video:**
```
{
    method: "share_video",
    description: "...",
    people_ids: "...",
    place_id: "...",
    preview_photo: [{
        caption: "...",
        image_url: "...",
        user_generated: "..."
    }, ...],
    reference: "...",
    title: "...",
    url: "...",
    video_url: "..."
}
```

For more details on the each of the additional dialog options refer to the
[Facebook SDK documentation](https://developers.facebook.com/docs/reference/android/4.5/).

### logEvent()

`facebookConnectPlugin.logEvent(String name, Object params, Number valueToSum, Function success, Function failure)`

This function allows you to publish app events that can help you to understand how users are engaging
with your app, measure the performance of your Facebook mobile app ads and reach specific sets of your
users with Facebook mobile app ads. Activation events are automatically tracked for you in the plugin.

* `name`, name of the event
* `params`, extra data to log with the event (is optional)
* `valueToSum`, a property which is an arbitrary number that can represent any value (e.g., a price or a quantity). When reported, all of the valueToSum properties will be summed together. For example, if 10 people each purchased one item that cost $10 (and passed in valueToSum) then they would be summed to report a number of $100. (is optional)

If successful, the success function will be called with no arguments.

If any error occurs the failure function will be called and passed an error message.

**Sample**

```
facebookConnectPlugin.logEvent("Test event", {}, 321,
    function (result) { console.log(result) },
    function (error) { alert(error) }
);
```

For more information see:
* [List of events](https://www.facebook.com/insights/)
* [iOS documentation](https://developers.facebook.com/docs/ios/app-events)
* [Android documentation](https://developers.facebook.com/docs/android/app-events)

**Note**: The javascript Facebook SDK does not have an Events API, so on the web platform the plugin functions will
do nothing.

### logPurchase()

`facebookConnectPlugin.logPurchase(Number amount, String currency, Function success, Function failure)`

The currency is expected to be an [ISO 4217 currency code](http://en.wikipedia.org/wiki/ISO_4217).
Both parameters are required.

If successful, the success function will be called with no arguments.

If any error occurs the failure function will be called and passed an error message.

**Sample**

```
facebookConnectPlugin.logPurchase(999, "USD",
    function (result) { console.log(result) },
    function (error) { alert(error) }
);
```

**Note**: The javascript Facebook SDK does not have an Events API, so on the web platform the plugin functions will
do nothing.