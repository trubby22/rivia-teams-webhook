// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example.graphwebhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.EventMessageDetail;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.serializer.AdditionalDataManager;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import com.microsoft.graph.models.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents the information sent via SocketIO to subscribed clients when a new Teams channel
 * message notification is received
 */
public class NewChatMessageNotification {

  /** The display name of the sender */
//    public final String sender;

  /** The content of the message */
  //  public final String body;

  public final EventMessageDetail eventDetail;

  // eventmessagedetail
  //  type
  //  if call, callid
  public NewChatMessageNotification(
      @NonNull ChatMessage message,
      @NonNull final SubscriptionRecord subscription,
      @Autowired OAuth2AuthorizedClientService authorizedClientService,
      String jsonPayload) throws JsonProcessingException {
    Objects.requireNonNull(message);
    //        sender = message.from.user.displayName;
    //    sender = "dummySender";
    //        body = message.body.content;
    //    body = "dummyBody";
    System.out.println("New message received");
    //    System.out.println(new Gson().toJson(message));

    eventDetail = message.eventDetail;
    if (eventDetail != null) {
//      System.out.printf("New chat message received %s%n", new Gson().toJson(eventDetail));
      String oDataType = eventDetail.oDataType;
      System.out.println(oDataType);
      //      AdditionalDataManager manager = eventDetail.additionalDataManager();
      //      System.out.println(manager.entrySet());

      assert oDataType != null;
      if (oDataType.contains("callEndedEventMessageDetail")) {
        ChatMessage chatMessage = new ChatMessage();
        ItemBody body = new ItemBody();
//        int randomNum = ThreadLocalRandom.current().nextInt(0, 100 + 1);
//        body.content = "Call ended " + randomNum;
        body.contentType = BodyType.HTML;
        body.content = "Please review the meeting. <attachment id=\"153fa47d-18c9-4179-be08-9879815a9f90\"></attachment>";
        chatMessage.body = body;
        LinkedList<ChatMessageAttachment> attachmentsList = new LinkedList<ChatMessageAttachment>();
        ChatMessageAttachment attachments = new ChatMessageAttachment();
        attachments.id = "153fa47d-18c9-4179-be08-9879815a9f90";
        attachments.contentType = "reference";
        attachments.contentUrl = "https://app.rivia.me";
        attachments.name = "app.rivia.me";
        attachmentsList.add(attachments);
        chatMessage.attachments = attachmentsList;

//        final var oauthClient =
//            authorizedClientService.loadAuthorizedClient("graph", subscription.userId);

        final var graphClient =
            GraphClientHelper.getGraphClient(WatchController.oauthClient2);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonPayload);
        String data = rootNode.get("value").get(0).get("resource").asText();
        System.out.println();
        System.out.println(data);

        String teamIdTemp = data.split("teams")[1].split("/channels")[0];
        String teamId = teamIdTemp.substring(2, teamIdTemp.length() - 2);

        String channelIdTemp = data.split("/channels")[1].split("/messages")[0];
        String channelId = channelIdTemp.substring(2, channelIdTemp.length() - 2);

        String messageIdTemp = data.split("/messages")[1].split("/replies")[0];
        String messageId = messageIdTemp.substring(2, messageIdTemp.length() - 2);

        graphClient
            .teams(teamId)
            .channels(channelId)
            .messages(messageId)
            .replies()
            .buildRequest()
            .post(chatMessage);
      }
    }
  }
}
