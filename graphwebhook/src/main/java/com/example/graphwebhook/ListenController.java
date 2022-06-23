// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example.graphwebhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.models.ItemBody;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.microsoft.graph.logger.DefaultLogger;
import com.microsoft.graph.models.ChangeNotification;
import com.microsoft.graph.models.ChangeNotificationCollection;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.serializer.DefaultSerializer;

import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@RestController
public class ListenController {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private SubscriptionStoreService subscriptionStore;

  @Autowired private CertificateStoreService certificateStore;

  @Autowired private OAuth2AuthorizedClientService authorizedClientService;

  private final SocketIONamespace socketIONamespace;

  @Value("${azure.activedirectory.client-id}")
  private String clientId;

  @Value("${azure.activedirectory.tenant-id}")
  private String tenantId;

  @Value("${azure.activedirectory.keydiscoveryurl}")
  private String keyDiscoveryUrl;

  @Autowired
  public ListenController(SocketIOServer socketIOServer) {
    // Set up a SocketIO server namespace to broadcast
    // incoming notifications to clients (browser)
    socketIONamespace = socketIOServer.addNamespace("/emitNotification");
    socketIONamespace.addEventListener(
        "create_room",
        String.class,
        new DataListener<String>() {
          @Override
          public void onData(SocketIOClient client, String roomName, AckRequest ackSender)
              throws Exception {
            log.info(
                "Client {} creating room for subscription {}", client.getSessionId(), roomName);
            client.joinRoom(roomName);
          }
        });
  }

  /**
   * This method handles the initial <a
   * href="https://docs.microsoft.com/graph/webhooks#notification-endpoint-validation">endpoint
   * validation request sent</a> by Microsoft Graph when the subscription is created.
   *
   * @param validationToken A validation token provided as a query parameter
   * @return a 200 OK response with the validationToken in the text/plain body
   */
  @PostMapping(
      value = "/listen",
      headers = {"content-type=text/plain"})
  @ResponseBody
  public ResponseEntity<String> handleValidation(
      @RequestParam(value = "validationToken") final String validationToken) {
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(validationToken);
  }

  /**
   * This method receives and processes incoming notifications from Microsoft Graph
   *
   * @param jsonPayload the JSON body of the request
   * @return A 202 Accepted response
   */
  @PostMapping("/listen")
  public CompletableFuture<ResponseEntity<String>> handleNotification(
      @RequestBody final String jsonPayload) throws IOException {

    System.out.println("New notification received");
    System.out.println();
    System.out.println(jsonPayload);
    System.out.println();

    // Deserialize the JSON body into a ChangeNotificationCollection
    final var serializer = new DefaultSerializer(new DefaultLogger());
    final var notifications =
        serializer.deserializeObject(jsonPayload, ChangeNotificationCollection.class);

    // Check for validation tokens
    boolean areTokensValid = true;
    if (notifications.validationTokens != null && !notifications.validationTokens.isEmpty()) {
      areTokensValid =
          TokenHelper.areValidationTokensValid(
              new String[] {clientId},
              new String[] {tenantId},
              notifications.validationTokens,
              keyDiscoveryUrl);
    }

    if (areTokensValid) {
      for (ChangeNotification notification : notifications.value) {
        // Look up subscription in store
        var subscription =
            subscriptionStore.getSubscription(notification.subscriptionId.toString());

        //        processOtherNotification(notification, subscription);
        // Only process if we know about this subscription AND
        // the client state in the notification matches
        //        processOtherNotification(notification, subscription);
        if (subscription != null && subscription.clientState.equals(notification.clientState)) {
          if (notification.encryptedContent == null) {
            // No encrypted content, this is a new message notification
            // without resource data
            processNewMessageNotification(notification, subscription, jsonPayload);
          } else {
            // With encrypted content, this is a new channel message
            // notification with encrypted resource data
            processNewChannelMessageNotification(
                notification,
                subscription,
                jsonPayload);
//            processNewMessageNotification(notification, subscription, jsonPayload);
          }
        }
      }
    }

    return CompletableFuture.completedFuture(ResponseEntity.accepted().body(""));
  }

  /**
   * Processes a new message notification by getting the message from Microsoft Graph
   *
   * @param notification the new message notification
   * @param subscription the matching subscription record
   */
  private void processNewMessageNotification(
      @NonNull final ChangeNotification notification,
      @NonNull final SubscriptionRecord subscription,
      String jsonPayload) {
    Objects.requireNonNull(notification);
    Objects.requireNonNull(subscription);

    // Get the authorized OAuth2 client for the relevant user
    // This allows the service to access the user's mailbox with delegated auth
    final var oauthClient =
        authorizedClientService.loadAuthorizedClient("graph", subscription.userId);

    final var graphClient = GraphClientHelper.getGraphClient(oauthClient);

//    ChatMessage chatMessageTemp = new ChatMessage();
//    ItemBody body = new ItemBody();
//    body.content = "Hello from Java";
//    chatMessageTemp.body = body;
//
//    graphClient
//        .teams("b158572b-3596-4340-927e-659fe6d916d0")
//        .channels("19:403409664c6249149d53e882a7647df7@thread.tacv2")
//        .messages()
//        .buildRequest()
//        .post(chatMessageTemp);

//    // Decrypt the encrypted key from the notification
//    final var decryptedKey =
//        certificateStore.getEncryptionKey(notification.encryptedContent.dataKey);
//
//    // Validate the signature
//    if (certificateStore.isDataSignatureValid(
//        decryptedKey,
//        notification.encryptedContent.data,
//        notification.encryptedContent.dataSignature)) {
//      // Decrypt the data using the decrypted key
//      final var decryptedData =
//          certificateStore.getDecryptedData(decryptedKey, notification.encryptedContent.data);
//      //      System.out.println(decryptedData);
//      //       Deserialize the decrypted JSON into a ChatMessage
//      final var serializer = new DefaultSerializer(new DefaultLogger());
//      final var chatMessage = serializer.deserializeObject(decryptedData, ChatMessage.class);
//      // Send the information to subscribed clients
//      socketIONamespace
//          .getRoomOperations(subscription.subscriptionId)
//          .sendEvent("notificationReceived", new NewChatMessageNotification(chatMessage));
//    }

    // The notification contains the relative URL to the message
    // so use the customRequest method instead of the fluent API
    // Once message has been retrieved, send the information via SocketIO
    // to subscribed clients
        graphClient
            .customRequest("/" + notification.resource, ChatMessage.class)
            .buildRequest()
            .getAsync()
            .thenAccept(
                message ->
                {
                  try {
                    socketIONamespace
                        .getRoomOperations(subscription.subscriptionId)
                        .sendEvent("notificationReceived",
                            new NewChatMessageNotification(
//                                message,
//                                subscription,
//                                authorizedClientService,
                                jsonPayload));
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
  }

  /**
   * Processes a new channel message notification by decrypting the included resource data
   *
   * @param notification the new channel message notification
   * @param subscription the matching subscription record
   */
  private void processNewChannelMessageNotification(
      @NonNull final ChangeNotification notification,
      @NonNull final SubscriptionRecord subscription,
      String jsonPayload)
      throws IOException {
    Objects.requireNonNull(notification);
    Objects.requireNonNull(subscription);

    // Decrypt the encrypted key from the notification
    final var decryptedKey =
        certificateStore.getEncryptionKey(notification.encryptedContent.dataKey);

    // Validate the signature
    if (certificateStore.isDataSignatureValid(
        decryptedKey,
        notification.encryptedContent.data,
        notification.encryptedContent.dataSignature)) {
      // Decrypt the data using the decrypted key
      final var decryptedData =
          certificateStore.getDecryptedData(decryptedKey, notification.encryptedContent.data);
      System.out.println(decryptedData);
      System.out.println();

      //       Deserialize the decrypted JSON into a ChatMessage
      final var serializer = new DefaultSerializer(new DefaultLogger());
      final var chatMessage = serializer.deserializeObject(decryptedData, ChatMessage.class);
      // Send the information to subscribed clients
      socketIONamespace
          .getRoomOperations(subscription.subscriptionId)
          .sendEvent("notificationReceived",
              new NewChatMessageNotification(
//                  chatMessage,
//                  subscription,
//                  authorizedClientService,
                  decryptedData));
    }
  }

  private void processOtherNotification(
      @NonNull final ChangeNotification notification,
      @NonNull final SubscriptionRecord subscription) {
    Objects.requireNonNull(notification);
    Objects.requireNonNull(subscription);

    System.out.println(notification);
    System.out.println(notification.id);
    System.out.println(notification.additionalDataManager());

    //    // Decrypt the encrypted key from the notification
    //    final var decryptedKey =
    //        certificateStore.getEncryptionKey(notification.encryptedContent.dataKey);
    //
    //    //     Validate the signature
    //    if (certificateStore.isDataSignatureValid(
    //        decryptedKey,
    //        notification.encryptedContent.data,
    //        notification.encryptedContent.dataSignature)) {
    //      // Decrypt the data using the decrypted key
    //      final var decryptedData =
    //          certificateStore.getDecryptedData(decryptedKey, notification.encryptedContent.data);
    //
    //      System.out.println(decryptedData);
    // Deserialize the decrypted JSON into a ChatMessage
    //      final var serializer = new DefaultSerializer(new DefaultLogger());
    //      final var chatMessage = serializer.deserializeObject(decryptedData,
    // ChatMessage.class);
    //      // Send the information to subscribed clients
    //      socketIONamespace
    //          .getRoomOperations(subscription.subscriptionId)
    //          .sendEvent("notificationReceived", new
    //     NewChatMessageNotification(chatMessage));
    //    }
  }
}
