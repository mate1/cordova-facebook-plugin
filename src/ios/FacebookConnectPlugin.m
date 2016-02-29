//
//  FacebookConnectPlugin.m
//  GapFacebookConnect
//
//  Created by Jesse MacFadyen on 11-04-22.
//  Updated by Mathijs de Bruin on 11-08-25.
//  Updated by Christine Abernathy on 13-01-22.
//  Updated by Marc-Andre Lamothe on 15-12-15.
//  Copyright 2011 Nitobi, Mathijs de Bruin. All rights reserved.
//

#import "FacebookConnectPlugin.h"

@interface FacebookConnectPlugin () <FBSDKAppGroupAddDialogDelegate, FBSDKAppGroupJoinDialogDelegate, FBSDKAppInviteDialogDelegate, FBSDKGameRequestDialogDelegate, FBSDKSharingDelegate>

@property (strong, nonatomic) NSString* loginCallbackId;
@property (strong, nonatomic) FBSDKLoginManager* loginManager;
@property (strong, nonatomic) NSString* dialogCallbackId;
@property (strong, nonatomic) NSString* graphCallbackId;
@property (strong, nonatomic) NSString* graphPath;

@end

@implementation FacebookConnectPlugin


- (CDVPlugin *)initWithWebView:(UIWebView *)theWebView {
    NSLog(@"Init FacebookConnect");
    self = (FacebookConnectPlugin *)[super initWithWebView:theWebView];
    
    // Initialize Facebook SDK Login Manager
    self.loginManager = [[FBSDKLoginManager alloc] init];
    
    // Add notification listener for tracking app activity with FB Events
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(applicationDidBecomeActive) name:UIApplicationDidBecomeActiveNotification object:nil];
    
    return self;
}

- (void)applicationDidBecomeActive {
    // Call the 'activateApp' method to log an app event for use in analytics and advertising reporting.
    [FBSDKAppEvents activateApp];
}

- (void)getAccessToken:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate sendPluginResult:[self accessTokenResponse] callbackId:command.callbackId];
}

- (void)logEvent:(CDVInvokedUrlCommand *)command {
    if ([command.arguments count] == 0) {
        // Not enough arguments
        CDVPluginResult *res = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid arguments"];
        [self.commandDelegate sendPluginResult:res callbackId:command.callbackId];
        return;
    }
    [self.commandDelegate runInBackground:^{
        // For more verbose output on logging uncomment the following:
        // [FBSettings setLoggingBehavior:[NSSet setWithObject:FBLoggingBehaviorAppEvents]];
        NSString *eventName = [command.arguments objectAtIndex:0];
        if ([command.arguments count] == 1) {
            [FBSDKAppEvents logEvent:eventName];
        } else {
            // argument count is not 0 or 1, must be 2 or more
            NSDictionary *params = [command.arguments objectAtIndex:1];
            if ([command.arguments count] == 2) {
                // If count is 2 we will just send params
                [FBSDKAppEvents logEvent:eventName parameters:params];
            }
            if ([command.arguments count] == 3) {
                // If count is 3 we will send params and a value to sum
                [FBSDKAppEvents logEvent:eventName valueToSum:[[command.arguments objectAtIndex:2] doubleValue] parameters:params];
            }
        }
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
    }];
}

- (void)logPurchase:(CDVInvokedUrlCommand *)command {
    /*
     While calls to logEvent can be made to register purchase events,
     there is a helper method that explicitly takes a currency indicator.
     */
    if ([command.arguments count] != 2) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid arguments"] callbackId:command.callbackId];
        return;
    }
    [FBSDKAppEvents logPurchase:[[command.arguments objectAtIndex:0] doubleValue] currency:[command.arguments objectAtIndex:1]];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
}

- (void)login:(CDVInvokedUrlCommand *)command {
    // Recover & validate permissions
    NSArray *permissions = nil;
    if ([command.arguments count] > 0) {
        permissions = command.arguments;
    }
    if (permissions == nil) {
        // We need permissions
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"No permissions specified at login"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    // save the callbackId for the login callback
    self.loginCallbackId = command.callbackId;
    
    [self performLogin:permissions callbackId:self.loginCallbackId];
}

- (void) logout:(CDVInvokedUrlCommand*)command
{
    // Close the session
    [self.loginManager logOut];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) showDialog:(CDVInvokedUrlCommand*)command
{
    __block BOOL error = NO;
    NSString* method;
    NSMutableDictionary *params;
    CDVPluginResult *pluginResult;
    
    @try {
        // Retrieve dialog method
        NSMutableDictionary *args = [[command.arguments lastObject] mutableCopy];
        method = [[NSString alloc] initWithString:[args objectForKey:@"method"]];
        [args removeObjectForKey:@"method"];
        
        // Retrieve dialog parameters
        params = [[NSMutableDictionary alloc] init];
        [args enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
            params[key] = obj;
        }];
        
#if !(__has_feature(objc_arc))
        [args release];
#endif
    }
    @catch (NSException *e) {
        error = YES;
    }
    
    if (error) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Missing or invalid parameters."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } else {
        // Save the callback ID
        self.dialogCallbackId = command.callbackId;
        
        // Check method
        if ([method isEqualToString:@"add_group"]) {
            FBSDKAppGroupContent *content = [[FBSDKAppGroupContent alloc] init];
            content.groupDescription = [params objectForKey:@"description"];
            content.name = [params objectForKey:@"name"];
            if ([[params objectForKey:@"privacy"] isEqual:@"Open"])
                content.privacy = FBSDKAppGroupPrivacyOpen;
            else
                content.privacy = FBSDKAppGroupPrivacyClosed;
            
            FBSDKAppGroupAddDialog *dialog = [FBSDKAppGroupAddDialog showWithContent:content delegate:self];
            
#if !(__has_feature(objc_arc))
            [content autorelease];
            [dialog autorelease];
#endif
        }
        else if ([method isEqualToString:@"app_invite"]) {
            FBSDKAppInviteContent *content = [[FBSDKAppInviteContent alloc] init];
            content.appInvitePreviewImageURL = [params objectForKey:@"preview_url"];
            content.appLinkURL = [params objectForKey:@"app_url"];
            
            FBSDKAppInviteDialog *dialog = [FBSDKAppInviteDialog showWithContent:content delegate:self];
            
#if !(__has_feature(objc_arc))
            [content autorelease];
            [dialog autorelease];
#endif
        }
        else if ([method isEqualToString:@"game_request"]) {
            FBSDKGameRequestContent *content = [[FBSDKGameRequestContent alloc] init];
            NSString *actionType = [params objectForKey:@"action_type"];
            if ([actionType isEqual:@"ASKFOR"])
                content.actionType = FBSDKGameRequestActionTypeAskFor;
            else if ([actionType isEqual:@"SEND"])
                content.actionType = FBSDKGameRequestActionTypeSend;
            else if ([actionType isEqual:@"TURN"])
                content.actionType = FBSDKGameRequestActionTypeTurn;
            else
                content.actionType = FBSDKGameRequestActionTypeNone;
            content.data = [params objectForKey:@"data"];
            if ([[params objectForKey:@"filters"] isEqual:@"APP_USERS"])
                content.filters = FBSDKGameRequestFilterAppUsers;
            else
                content.filters = FBSDKGameRequestFilterAppNonUsers;
            content.message = [params objectForKey:@"message"];
            content.objectID = [params objectForKey:@"object_id"];
            content.recipients = [[params objectForKey:@"recipient_ids"] componentsSeparatedByString:@","];
            content.recipientSuggestions = [[params objectForKey:@"suggested_recipient_ids"] componentsSeparatedByString:@","];
            content.title = [params objectForKey:@"title"];
            
            FBSDKGameRequestDialog *dialog = [FBSDKGameRequestDialog showWithContent:content delegate:self];
            
#if !(__has_feature(objc_arc))
            [content autorelease];
            [dialog autorelease];
#endif
        }
        else if ([method isEqualToString:@"join_group"]) {
            FBSDKAppGroupJoinDialog *dialog = [FBSDKAppGroupJoinDialog showWithGroupID:[params objectForKey:@"group_id"] delegate:self];
            
#if !(__has_feature(objc_arc))
            [dialog autorelease];
#endif
        }
        else if ([method isEqualToString:@"share_link"]) {
            FBSDKShareLinkContent *content = [[FBSDKShareLinkContent alloc] init];
            content.contentDescription = [params objectForKey:@"description"];
            content.contentTitle = [params objectForKey:@"title"];
            content.imageURL = [NSURL URLWithString:[params objectForKey:@"image_url"]];
            content.contentURL = [NSURL URLWithString:[params objectForKey:@"url"]];
            content.peopleIDs = [[params objectForKey:@"people_ids"] componentsSeparatedByString:@","];
            content.placeID = [params objectForKey:@"place_id"];
            content.ref = [params objectForKey:@"reference"];
            
            FBSDKShareDialog *dialog = [FBSDKShareDialog showFromViewController:self.viewController withContent:content delegate:self];
            
#if !(__has_feature(objc_arc))
            [content autorelease];
            [dialog autorelease];
#endif
        }
        else if ([method isEqualToString:@"share_photo"]) {
            NSMutableArray *photos = [[NSMutableArray alloc] initWithArray:@[]];
            
            for (id photoParams in [params objectForKey:@"photos"]) {
                FBSDKSharePhoto *photo = [[FBSDKSharePhoto alloc] init];
                photo.caption = [photoParams objectForKey:@"caption"];
                // photo.image = [photoParams objectForKey:@"image"];
                photo.imageURL = [photoParams objectForKey:@"image_url"];
                photo.userGenerated = [photoParams objectForKey:@"user_generated"] ? YES : NO;
            }
            
            FBSDKSharePhotoContent *content = [[FBSDKSharePhotoContent alloc] init];
            content.photos = photos;
            content.contentURL = [NSURL URLWithString:[params objectForKey:@"url"]];
            content.peopleIDs = [[params objectForKey:@"people_ids"] componentsSeparatedByString:@","];
            content.placeID = [params objectForKey:@"place_id"];
            content.ref = [params objectForKey:@"reference"];
            
            FBSDKShareDialog *dialog = [FBSDKShareDialog showFromViewController:self.viewController withContent:content delegate:self];
            
#if !(__has_feature(objc_arc))
            [content autorelease];
            [dialog autorelease];
            [photos autorelease];
#endif
        }
        else if ([method isEqualToString:@"share_video"]) {
            FBSDKSharePhoto *photo = [[FBSDKSharePhoto alloc] init];
            photo.caption = [[params objectForKey:@"preview_photo"] objectForKey:@"caption"];
            // photo.image = [[params objectForKey:@"preview_photo"] objectForKey:@"image"];
            photo.imageURL = [[params objectForKey:@"preview_photo"] objectForKey:@"image_url"];
            photo.userGenerated = [[params objectForKey:@"preview_photo"] objectForKey:@"user_generated"] ? YES : NO;
            
            FBSDKShareVideo *video = [[FBSDKShareVideo alloc] init];
            video.videoURL = [params objectForKey:@"video_url"];
            
            FBSDKShareVideoContent *content = [[FBSDKShareVideoContent alloc] init];
            content.previewPhoto = photo;
            content.video = video;
            content.contentURL = [NSURL URLWithString:[params objectForKey:@"url"]];
            content.peopleIDs = [[params objectForKey:@"people_ids"] componentsSeparatedByString:@","];
            content.placeID = [params objectForKey:@"place_id"];
            content.ref = [params objectForKey:@"reference"];
            
            FBSDKShareDialog *dialog = [FBSDKShareDialog showFromViewController:self.viewController withContent:content delegate:self];
            
#if !(__has_feature(objc_arc))
            [content autorelease];
            [dialog autorelease];
            [photo autorelease];
            [video autorelease];
#endif
        }
    }
    
#if !(__has_feature(objc_arc))
    [method release];
    [params autorelease];
#endif
}

- (void) graphApi:(CDVInvokedUrlCommand *)command
{
    // Save the callback ID and path
    self.graphCallbackId = command.callbackId;
    self.graphPath = [command argumentAtIndex:0];
    
    // We will store here the missing permissions that we will have to request
    NSMutableArray *missingPermissions = [[NSMutableArray alloc] initWithArray:@[]];
    NSArray *permissions = [command argumentAtIndex:1];
    
    // Check if all the permissions we need are present in the user's current permissions
    // If they are not present add them to the permissions to be requested
    FBSDKAccessToken *accessToken = [FBSDKAccessToken currentAccessToken];
    if (accessToken != nil && [accessToken.expirationDate compare:[NSDate date]] > 0) {
        for (NSString *permission in permissions) {
            if (![[accessToken permissions] containsObject:permission]) {
                [missingPermissions addObject:permission];
            }
        }
    }
    else {
        [missingPermissions addObjectsFromArray:permissions];
    }
    
    // If we have permissions to request
    if ([missingPermissions count] > 0) {
        [self performLogin:missingPermissions callbackId:self.graphCallbackId];
    } else {
        // Permissions are present
        // We can request the user information
        [self makeGraphCall];
    }
    
#if !(__has_feature(objc_arc))
    [missingPermissions autorelease];
#endif
}

- (void)handleDialogCanceled:(NSString *)dialogName {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"User cancelled %@ dialog.", dialogName]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.dialogCallbackId];
    self.dialogCallbackId = nil;
}

- (void)handleDialogError:(NSError *)error {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"Error: %@", error.description]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.dialogCallbackId];
    self.dialogCallbackId = nil;
}

- (void)handleDialogResult:(NSDictionary *)results {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:results];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.dialogCallbackId];
    self.dialogCallbackId = nil;
}


- (void)handleLoginResult:(FBSDKLoginManagerLoginResult *)result
                    error:(NSError *)error
{
    if (self.loginCallbackId) {
        // Initial login
        CDVPluginResult *pluginResult;
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedFailureReason]];
        }
        else {
            pluginResult = [self accessTokenResponse];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.loginCallbackId];
        self.loginCallbackId = nil;
    }
    else if (self.graphCallbackId) {
        // New permissions requested
        CDVPluginResult *pluginResult;
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedFailureReason]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:self.graphCallbackId];
            self.graphCallbackId = nil;
        }
        else {
            NSLog(@"new permissions %@", [[FBSDKAccessToken currentAccessToken] permissions]);
            // We can request the user information
            [self makeGraphCall];
        }
    }
}

- (void) makeGraphCall
{
    NSLog(@"Graph Path = %@", self.graphPath);
    
    NSArray *pairs = [self.graphPath componentsSeparatedByString:@"?"];
    NSDictionary *params = [self parseURLParams:[pairs[1] query]];
    
    FBSDKGraphRequestConnection *conn = [[FBSDKGraphRequestConnection alloc] init];
    FBSDKGraphRequest *request = [[FBSDKGraphRequest alloc] initWithGraphPath: pairs[0] parameters: params];
    [conn addRequest:request completionHandler:^(FBSDKGraphRequestConnection *connection, id result, NSError *error) {
        CDVPluginResult* pluginResult = nil;
        if (!error) {
            NSDictionary *response = (NSDictionary *) result;
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:response];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                             messageAsString:[error localizedDescription]];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.graphCallbackId];
        self.graphCallbackId = nil;
        self.graphPath = nil;
    }];
    [conn start];
    
#if !(__has_feature(objc_arc))
    [conn autorelease];
    [params autorelease];
    [request autorelease];
#endif
}

- (void)performLogin:(NSArray *)permissions
          callbackId:(NSString*)callbackId
{
    CDVPluginResult *pluginResult;
    BOOL publish = NO;
    BOOL read = NO;
    
    for (NSString *p in permissions) {
        if ([p hasPrefix:@"publish"] || [p hasPrefix:@"manage"] || [p isEqualToString:@"ads_management"] || [p isEqualToString:@"create_event"] || [p isEqualToString:@"rsvp_event"]) {
            publish = YES;
        } else {
            read = YES;
        }
        
        // If we've found one of each we can stop looking.
        if (publish && read) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                             messageAsString:@"Your app can't ask for both read and write permissions."];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
            return;
        }
    }
    
    if (publish) {
        [self.loginManager logInWithPublishPermissions:permissions handler:^(FBSDKLoginManagerLoginResult *result, NSError *error) {
             [self handleLoginResult:result error:error];
        }];
    } else {
        [self.loginManager logInWithReadPermissions:permissions handler:^(FBSDKLoginManagerLoginResult *result, NSError *error) {
             [self handleLoginResult:result error:error];
        }];
    }
}

- (NSDictionary *)accessTokenResponse {
    FBSDKAccessToken *accessToken = [FBSDKAccessToken currentAccessToken];
    if (accessToken != nil && [accessToken.expirationDate compare:[NSDate date]] > 0) {
        @try {
            NSDictionary *statusDict = [NSDictionary dictionaryWithObjectsAndKeys:
                accessToken.tokenString, @"accessToken",
                accessToken.declinedPermissions, @"declinedPermissions",
                [NSString stringWithFormat:@"%0.0f", MAX([accessToken.expirationDate timeIntervalSinceNow],0)], @"expiresIn",
                accessToken.refreshDate, @"lastRefresh",
                accessToken.permissions, @"permissions",
                accessToken.userID, @"userID",
                nil
            ];

            return [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:statusDict];
        }
        @catch (NSException *e) {
            return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"No valid access token found."];
        }
    }
    else
    {
        return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"No valid access token found."];
    }
}

/**
 * A method for parsing URL parameters.
 */
- (NSDictionary*)parseURLParams:(NSString *)query {
    NSString *regexStr = @"^(.+)\\[(.*)\\]$";
    NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:regexStr options:0 error:nil];

    NSArray *pairs = [query componentsSeparatedByString:@"&"];
    NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
    [pairs enumerateObjectsUsingBlock:
     ^(NSString *pair, NSUInteger idx, BOOL *stop) {
         NSArray *kv = [pair componentsSeparatedByString:@"="];
         NSString *key = [kv[0] stringByReplacingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
         NSString *val = [kv[1] stringByReplacingPercentEscapesUsingEncoding:NSUTF8StringEncoding];

         NSArray *matches = [regex matchesInString:key options:0 range:NSMakeRange(0, [key length])];
         if ([matches count] > 0) {
             for (NSTextCheckingResult *match in matches) {

                 NSString *newKey = [key substringWithRange:[match rangeAtIndex:1]];

                 if ([[params allKeys] containsObject:newKey]) {
                     NSMutableArray *obj = [params objectForKey:newKey];
                     [obj addObject:val];
                     [params setObject:obj forKey:newKey];
                 } else {
                     NSMutableArray *obj = [NSMutableArray arrayWithObject:val];
                     [params setObject:obj forKey:newKey];
                 }
             }
         } else {
             params[key] = val;
         }
    }];
    return params;
}

#pragma mark - FBSDKAppGroupAddDialogDelegate

- (void)appGroupAddDialog:(FBSDKAppGroupAddDialog *)appGroupAddDialog didCompleteWithResults:(NSDictionary *)results {
    [self handleDialogResult:results];
}

- (void)appGroupAddDialog:(FBSDKAppGroupAddDialog *)appGroupAddDialog didFailWithError:(NSError *)error {
    [self handleDialogError:error];
}

- (void)appGroupAddDialogDidCancel:(FBSDKAppGroupAddDialog *)appGroupAddDialog {
    [self handleDialogCanceled:@"add group"];
}


#pragma mark - FBSDKAppGroupJoinDialogDelegate

- (void)appGroupJoinDialog:(FBSDKAppGroupJoinDialog *)appGroupJoinDialog didCompleteWithResults:(NSDictionary *)results {
    [self handleDialogResult:results];
}

- (void)appGroupJoinDialog:(FBSDKAppGroupJoinDialog *)appGroupJoinDialog didFailWithError:(NSError *)error {
    [self handleDialogError:error];
}

- (void)appGroupJoinDialogDidCancel:(FBSDKAppGroupJoinDialog *)appGroupJoinDialog {
    [self handleDialogCanceled:@"join group"];
}

#pragma mark - FBSDKAppInviteDialogDelegate

- (void)appInviteDialog:(FBSDKAppInviteDialog *)appInviteDialog didCompleteWithResults:(NSDictionary *)results {
    [self handleDialogResult:results];
}

- (void)appInviteDialog:(FBSDKAppInviteDialog *)appInviteDialog didFailWithError:(NSError *)error {
    [self handleDialogError:error];
}

#pragma mark - FBSDKGameRequestDialogDelegate

- (void)gameRequestDialog:(FBSDKGameRequestDialog *)gameRequestDialog didCompleteWithResults:(NSDictionary *)results {
    [self handleDialogResult:results];
}

- (void)gameRequestDialog:(FBSDKGameRequestDialog *)gameRequestDialog didFailWithError:(NSError *)error {
    [self handleDialogError:error];
}

- (void)gameRequestDialogDidCancel:(FBSDKGameRequestDialog *)gameRequestDialog {
    [self handleDialogCanceled:@"game request"];
}

#pragma mark - FBSDKSharingDelegate

- (void)sharer:(id<FBSDKSharing>)sharer didCompleteWithResults:(NSDictionary *)results {
    [self handleDialogResult:results];
}

- (void)sharer:(id<FBSDKSharing>)sharer didFailWithError:(NSError *)error {
    [self handleDialogError:error];
}

- (void)sharerDidCancel:(id<FBSDKSharing>)sharer {
    [self handleDialogCanceled:@"sharing"];
}

@end
