// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example.graphwebhook;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.ItemBody;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import org.apache.http.entity.ContentType;
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

    @SerializedName("replyToId")
    String replyToId;

    @SerializedName("channelIdentity")
    ChannelIdentity channelIdentity;
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

  private class ChannelIdentity {
    @SerializedName("teamId")
    String teamId;

    @SerializedName("channelId")
    String channelId;
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

  private class PostMeetingResponse {
    @SerializedName("meetingId")
    String meetingId;
  }

  public NewChatMessageNotification(
      //      @NonNull ChatMessage message,
      //      @NonNull final SubscriptionRecord subscription,
      //      @Autowired OAuth2AuthorizedClientService authorizedClientService,
      String decryptedData) throws IOException {
    System.out.println("New message received");
    SdkHttpClient httpClient = ApacheHttpClient.builder().build();
    Gson gson = new Gson();
    DecryptedResourceData decryptedResourceData =
        gson.fromJson(decryptedData, DecryptedResourceData.class);

    EventDetail eventDetail = decryptedResourceData.eventDetail;
    if (eventDetail != null && eventDetail.odataType != null) {
      String odataType = eventDetail.odataType;
      System.out.println(odataType);

      if (odataType.contains("callEndedEventMessageDetail")) {
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
        Meeting meeting = new Meeting(title, startTime, endTime, organizerId, participants);
        String body = gson.toJson(meeting);
        System.out.println(body);
        String tenantId =
            decryptedResourceData.webUrl.split("tenantId=")[1].split("&createdTime=")[0];

        var sdkHttpRequestBuilder =
            SdkHttpRequest.builder()
                .uri(URI.create("https://api.rivia.me/meetings"))
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
        int statusCode = response.httpResponse().statusCode();
        String responseBody =
            new String(
                response.responseBody().get().delegate().readAllBytes(), StandardCharsets.UTF_8);
        if (!response.httpResponse().isSuccessful()) {
          throw new Error(statusCode + responseBody);
        }
        System.out.println(statusCode);
        System.out.println(responseBody);
        PostMeetingResponse postMeetingResponse =
            gson.fromJson(responseBody, PostMeetingResponse.class);
        String meetingId = postMeetingResponse.meetingId;

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.subject = null;
        ItemBody itemBody = new ItemBody();
        itemBody.contentType = BodyType.HTML;
        itemBody.content = "<attachment id=\"74d20c7f34aa4a7fb74e2b30004247c5\"></attachment>";
        chatMessage.body = itemBody;
        LinkedList<ChatMessageAttachment> attachmentsList = new LinkedList<>();
        ChatMessageAttachment attachments = new ChatMessageAttachment();
        attachments.id = "74d20c7f34aa4a7fb74e2b30004247c5";
        attachments.contentType = "application/vnd.microsoft.card.thumbnail";
        attachments.contentUrl = null;
        attachments.content =
            String.format("""
                {
                  "subtitle": "<h3>Please rate the meeting</h3>",
                  "text": "<a href=\\"https://app.rivia.me/?meetingId=%s\\">app.rivia.me</a>"
                }""", meetingId);
        attachments.name = null;
        attachments.thumbnailUrl = null;
        attachmentsList.add(attachments);
        chatMessage.attachments = attachmentsList;

        final var graphClient = GraphClientHelper.getGraphClient(WatchController.oauthClient2);

        graphClient
            .teams(decryptedResourceData.channelIdentity.teamId)
            .channels(decryptedResourceData.channelIdentity.channelId)
            .messages(decryptedResourceData.replyToId)
            .replies()
            .buildRequest()
            .post(chatMessage);
      }
    }
  }
}
