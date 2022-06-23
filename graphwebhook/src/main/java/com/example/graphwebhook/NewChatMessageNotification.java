// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example.graphwebhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.EventMessageDetail;
import com.microsoft.graph.models.ItemBody;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * Represents the information sent via SocketIO to subscribed clients when a new Teams channel
 * message notification is received
 */
public class NewChatMessageNotification {

  private class DecryptedResourceData {
    @SerializedName("id")
    String id;

    @SerializedName("createdDateTime")
    String createdDateTime;

    @SerializedName("eventDetail")
    EventDetail eventDetail;

    @SerializedName("subject")
    String subject;

    @SerializedName("webUrl")
    String webUrl;
  }

  private class EventDetail {
    @SerializedName("@odata.type")
    String odataType;

    @SerializedName("callDuration")
    String callDuration;

    @SerializedName("callParticipants")
    List<ParticipantWrapper> callParticipants;

    @SerializedName("initiator")
    Participant initiator;
  }

  private class ParticipantWrapper {
    @SerializedName("participant")
    Participant participant;
  }

  private class Participant {
    @SerializedName("user")
    User user;
  }

  private class User {
    @SerializedName("id")
    String id;
  }

  private static class Meeting {
    @SerializedName("title")
    String title;

    @SerializedName("startTime")
    int startTime;

    @SerializedName("endTime")
    int endTime;

    @SerializedName("organizerId")
    String organizerId;

    @SerializedName("userIds")
    List<String> userIds;

    private Meeting(
        String title, int startTime, int endTime, String organizerId, List<String> userIds) {
      this.title = title;
      this.startTime = startTime;
      this.endTime = endTime;
      this.organizerId = organizerId;
      this.userIds = userIds;
    }
  }

  public NewChatMessageNotification(
      @NonNull ChatMessage message,
      @NonNull final SubscriptionRecord subscription,
      @Autowired OAuth2AuthorizedClientService authorizedClientService,
      String decryptedData)
      throws IOException {
    Objects.requireNonNull(message);
    System.out.println("New message received");
    SdkHttpClient httpClient = ApacheHttpClient.builder().build();

    EventMessageDetail eventDetail = message.eventDetail;
    if (eventDetail != null) {
      String oDataType = eventDetail.oDataType;
      System.out.println(oDataType);

      assert oDataType != null;
      if (oDataType.contains("callEndedEventMessageDetail")) {
        Gson gson = new Gson();
        DecryptedResourceData decryptedResourceData =
            gson.fromJson(decryptedData, DecryptedResourceData.class);
        OffsetDateTime endTimeTemp = OffsetDateTime.parse(decryptedResourceData.createdDateTime);
        int endTime = (int) endTimeTemp.toEpochSecond();
        Duration duration = Duration.parse(decryptedResourceData.eventDetail.callDuration);
        OffsetDateTime startTimeTemp = endTimeTemp.minus(duration);
        int startTime = (int) startTimeTemp.toEpochSecond();
        List<String> participants =
            decryptedResourceData.eventDetail.callParticipants.stream()
                .map(it -> it.participant.user.id)
                .toList();
        String organizerId = decryptedResourceData.eventDetail.initiator.user.id;
        String title = "Teams Meeting";

        System.out.println(title);
        System.out.println(startTime);
        System.out.println(endTime);
        System.out.println(organizerId);
        System.out.println(participants);

        Meeting meeting = new Meeting(title, startTime, endTime, organizerId, participants);

        String body = gson.toJson(meeting);
        System.out.println(body);

        String tenantIdTemp =
            decryptedResourceData.webUrl.split("tenantId=")[1].split("&createdTime=")[0];
        String tenantId = tenantIdTemp.substring(1, tenantIdTemp.length() - 1);

        var sdkHttpRequestBuilder =
            SdkHttpRequest.builder()
                .uri(URI.create("https://api.rivia.me/meetings?tenant=" + tenantId))
                .method(SdkHttpMethod.POST)
                .appendRawQueryParameter("tenant", tenantId)
                .appendHeader("Content-Type", "application/json");
        var httpExecuteRequestBuilder =
            HttpExecuteRequest.builder().request(sdkHttpRequestBuilder.build());
        if (body != null) {
          httpExecuteRequestBuilder =
              httpExecuteRequestBuilder.contentStreamProvider(
                  () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
        HttpExecuteResponse response =
            httpClient.prepareRequest(httpExecuteRequestBuilder.build()).call();
        if (!response.httpResponse().isSuccessful()) {
          return;
        }

        ChatMessage chatMessage = new ChatMessage();
        ItemBody messageBody = new ItemBody();
        messageBody.contentType = BodyType.HTML;
        messageBody.content =
            "Please review the meeting. <attachment id=\"153fa47d-18c9-4179-be08-9879815a9f90\"></attachment>";
        chatMessage.body = messageBody;
        LinkedList<ChatMessageAttachment> attachmentsList = new LinkedList<ChatMessageAttachment>();
        ChatMessageAttachment attachments = new ChatMessageAttachment();
        attachments.id = "153fa47d-18c9-4179-be08-9879815a9f90";
        attachments.contentType = "reference";
        attachments.contentUrl = "https://app.rivia.me";
        attachments.name = "app.rivia.me";
        attachmentsList.add(attachments);
        chatMessage.attachments = attachmentsList;

        final var graphClient = GraphClientHelper.getGraphClient(WatchController.oauthClient2);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(decryptedData);
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
